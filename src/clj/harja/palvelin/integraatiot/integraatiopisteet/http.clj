(ns harja.palvelin.integraatiot.integraatiopisteet.http
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn rakenna-http-kutsu [metodi otsikot parametrit kutsudata]
  (let [kutsu {}]
    (-> kutsu
        (cond-> (not-empty otsikot) (assoc :headers otsikot))
        (cond-> (not-empty parametrit) (assoc :query-params parametrit))
        (cond-> (or (= metodi "post") (= metodi "put")) (assoc :body kutsudata)))))

(defn tee-http-kutsu [url metodi otsikot parametrit kutsudata]
  (let [kutsu (rakenna-http-kutsu metodi otsikot parametrit kutsudata)]
    (case metodi
      "post" @(http/post url kutsu)
      "get" @(http/get url kutsu)
      "put" @(http/put url kutsu)
      "delete" @(http/delete url kutsu)
      (throw+
        {:type    :http-kutsu-epaonnistui
         :virheet [{:koodi :tuntematon-http-metodi :viesti (str "Tuntematon HTTP metodi:" metodi)}]}))))

(defn laheta-kutsu [integraatioloki integraatio jarjestelma url metodi otsikot parametrit kutsudata kasittele-vastaus]
  (log/debug " Lähetetään HTTP " metodi " -kutsu integraatiolle: " integraatio ", järjestelmään: " jarjestelma " : "
             " - osoite: " url ", "
             " - metodi: " metodi ", "
             " - data: " kutsudata ", "
             " - otsikkot: " otsikot
             " - parametrit: " parametrit)

  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki jarjestelma integraatio nil nil)
        sisaltotyyppi (get otsikot " Content-Type ")]
    (try
      (integraatioloki/kirjaa-rest-viesti integraatioloki tapahtuma-id " ulos " url sisaltotyyppi kutsudata otsikot nil)
      (let [{:keys [status body error headers]} (tee-http-kutsu url metodi otsikot parametrit kutsudata)
            lokiviesti (integraatioloki/tee-rest-lokiviesti " sisään " url sisaltotyyppi body headers nil)]
        (log/debug " Palvelu palautti: ")
        (log/debug " - tila: " status)
        (log/debug " - otsikot: " headers)
        (log/debug " - data: " body)

        (if (or error (not (= 200 status)))
          (do
            (log/error " Kutsu palveluun: " url " epäonnistui virhe: " error)
            (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti (str " Virhe: " error) tapahtuma-id nil)
            (throw+ {:type :http-kutsu-epaonnistui :error error}))
          (do
            (let [vastausdata (kasittele-vastaus body)]
              (log/debug " Kutsu palveluun: " url " onnistui. ")
              (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti nil tapahtuma-id nil)
              vastausdata))))

      (catch Exception e
        ;; todo: lisää mikä järjestelmä & mikä integraatio!
        (log/error " HTTP-kutsukäsittelyssä tapahtui poikkeus: " e " (järjestelmä: " jarjestelma ", integraatio: " integraatio ", URL: " url ") ")
        (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki nil (str " Tapahtui poikkeus: " e) tapahtuma-id nil)
        (throw+ {:type :http-kutsu-epaonnistui :error e})))))

(defn laheta-post-kutsu [integraatioloki integraatio jarjestelma url otsikot parametrit kutsudata kasittele-vastaus-fn]
  (laheta-kutsu integraatioloki integraatio jarjestelma url " post " otsikot parametrit kutsudata kasittele-vastaus-fn))