(ns harja.palvelin.integraatiot.integraatiopisteet.http
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn rakenna-http-kutsu [metodi otsikot parametrit kutsudata]
  (let [kutsu {}]
    (-> kutsu
        (cond-> (not-empty otsikot) (assoc :headers otsikot))
        (cond-> (not-empty parametrit) (assoc :query-params parametrit))
        (cond-> (or (= metodi "post") (= metodi "put")) (assoc :body kutsudata)))))

(defn tee-http-kutsu [integraatioloki jarjestelma integraatio tapahtuma-id url metodi otsikot parametrit kutsudata]
  (try
    (let [kutsu (rakenna-http-kutsu metodi otsikot parametrit kutsudata)]
      (case metodi
        "post" @(http/post url kutsu)
        "get" @(http/get url kutsu)
        "put" @(http/put url kutsu)
        "delete" @(http/delete url kutsu)
        "head" @(http/head url kutsu)
        (throw+
          {:type    virheet/+ulkoinen-kasittelyvirhe-koodi+
           :virheet [{:koodi :tuntematon-http-metodi :viesti (str "Tuntematon HTTP metodi:" metodi)}]})))
    (catch Exception e
      (log/error " HTTP-kutsukäsittelyssä tapahtui poikkeus: " e " (järjestelmä: " jarjestelma ", integraatio: " integraatio ", URL: " url ") ")
      (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki nil (str " Tapahtui poikkeus: " e) tapahtuma-id nil)
      (throw+
        {:type    virheet/+ulkoinen-kasittelyvirhe-koodi+
         :virheet [{:koodi :poikkeus :viesti (str "Poikkeus :" (.getMessage e))}]}))))

(defn laheta-kutsu [integraatioloki integraatio jarjestelma url metodi otsikot parametrit kutsudata kasittele-vastaus]
  (log/debug " Lähetetään HTTP " metodi "-kutsu integraatiolle: " integraatio ", järjestelmään: " jarjestelma " : "
             "\n - osoite: " url ", "
             "\n - metodi: " metodi ", "
             "\n - data: " kutsudata ", "
             "\n - otsikkot: " otsikot
             "\n - parametrit: " parametrit)

  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki jarjestelma integraatio nil nil)
        sisaltotyyppi (get otsikot " Content-Type ")]

    (integraatioloki/kirjaa-rest-viesti integraatioloki tapahtuma-id "ulos" url sisaltotyyppi kutsudata otsikot nil)
    (let [{:keys [status body error headers]} (tee-http-kutsu integraatioloki jarjestelma integraatio tapahtuma-id url metodi otsikot parametrit kutsudata)
          lokiviesti (integraatioloki/tee-rest-lokiviesti "sisään" url sisaltotyyppi body headers nil)]
      (log/debug " Palvelu palautti: ")
      (log/debug " - tila: " status)
      (log/debug " - otsikot: " headers)
      (log/debug " - data: " body)

      (if (or error (not (= 200 status)))
        (do
          (log/error " Kutsu palveluun: " url " epäonnistui virhe: " error)
          (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti (str " Virhe: " error) tapahtuma-id nil)
          (throw+ {:type    virheet/+ulkoinen-kasittelyvirhe-koodi+
                   :virheet [{:koodi :ulkoinen-jarjestelma-palautti-virheen :viesti (str "Virhe :" error)}]}))
        (do
          (let [vastausdata (kasittele-vastaus body headers)]
            (log/debug " Kutsu palveluun: " url " onnistui. ")
            (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti nil tapahtuma-id nil)
            vastausdata))))))

(defn laheta-get-kutsu [integraatioloki integraatio jarjestelma url otsikot parametrit kasittele-vastaus-fn]
  (laheta-kutsu integraatioloki integraatio jarjestelma url "get" otsikot parametrit nil kasittele-vastaus-fn))

(defn laheta-post-kutsu [integraatioloki integraatio jarjestelma url otsikot parametrit kutsudata kasittele-vastaus-fn]
  (laheta-kutsu integraatioloki integraatio jarjestelma url "post" otsikot parametrit kutsudata kasittele-vastaus-fn))

(defn laheta-head-kutsu [integraatioloki integraatio jarjestelma url otsikot parametrit kasittele-vastaus-fn]
  (laheta-kutsu integraatioloki integraatio jarjestelma url "head" otsikot parametrit nil kasittele-vastaus-fn))
