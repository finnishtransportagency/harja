(ns harja.palvelin.integraatiot.integraatiopisteet.http
  "Yleiset apurit kutsujen lähettämiseen ulkoisiin järjestelmiin.
  Sisältää automaattiset lokitukset integraatiolokiin."
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.net ConnectException)
           (org.httpkit.client TimeoutException)))

(def timeout-aika-ms 10000)

(defn rakenna-http-kutsu [{:keys [metodi otsikot parametrit kayttajatunnus salasana kutsudata timeout] :as optiot}]
  (let [kutsu {}]
    (-> kutsu
        (cond-> (not-empty otsikot) (assoc :headers otsikot)
                (not-empty parametrit) (assoc :query-params parametrit)
                (and (not-empty kayttajatunnus)) (assoc :basic-auth [kayttajatunnus salasana])
                (or (= metodi "post") (= metodi "put")) (assoc :body kutsudata)
                timeout (assoc :timeout timeout)))))

(defn tee-http-kutsu [lokittaja tapahtuma-id url metodi otsikot parametrit kayttajatunnus salasana kutsudata]
  (try
    (let [kutsu (rakenna-http-kutsu {:metodi metodi :otsikot otsikot :parametrit parametrit :kayttajatunnus kayttajatunnus
                                     :salasana salasana :kutsudata kutsudata :timeout timeout-aika-ms})]
      (case metodi
        :post @(http/post url kutsu)
        :get @(http/get url kutsu)
        :put @(http/put url kutsu)
        :delete @(http/delete url kutsu)
        :head @(http/head url kutsu)
        (throw+
          {:type virheet/+ulkoinen-kasittelyvirhe-koodi+
           :virheet [{:koodi :tuntematon-http-metodi :viesti (str "Tuntematon HTTP metodi:" metodi)}]})))
    (catch Exception e
      (log/error e (format "HTTP-kutsukäsittelyssä tapahtui poikkeus. (järjestelmä: %s, integraatio: %s, URL: %s)" jarjestelma integraatio url))
      (lokittaja :epaonnistunut nil (str " Tapahtui poikkeus: " e) tapahtuma-id)
      (throw+
        {:type virheet/+ulkoinen-kasittelyvirhe-koodi+
         :virheet [{:koodi :poikkeus :viesti (str "HTTP-kutsukäsittelyssä tapahtui odottamaton virhe.")}]}))))

(defn laheta-kutsu
  ([lokittaja url metodi otsikot parametrit kutsudata kasittele-vastaus]
   (laheta-kutsu lokittaja url metodi otsikot parametrit nil nil kutsudata kasittele-vastaus))
  ([lokittaja url metodi otsikot parametrit kayttajatunnus salasana kutsudata kasittele-vastaus]
   (log/debug (format "Lähetetään HTTP %s -kutsu: osoite: %s, metodi: %s, data: %s, otsikkot: %s, parametrit: %s"
                      metodi url metodi kutsudata otsikot parametrit))

   (let [tapahtuma-id (lokittaja :alkanut)
         sisaltotyyppi (get otsikot " Content-Type ")]

     (lokittaja :rest-viesti tapahtuma-id "ulos" url sisaltotyyppi kutsudata otsikot (str parametrit))

     (let [{:keys [status body error headers]} (tee-http-kutsu lokittaja tapahtuma-id url metodi otsikot parametrit kayttajatunnus salasana kutsudata)
           lokiviesti (integraatioloki/tee-rest-lokiviesti "sisään" url sisaltotyyppi body headers nil)]
       (log/debug (format " Palvelu palautti: tila: %s , otsikot: %s , data: %s" status headers body))

       (if (or error
               (not (= 200 status)))
         (do
           (log/error (format "Kutsu palveluun: %s epäonnistui. Virhe: %s " url error))
           (log/error "Virhetyyppi: " (type error))
           (lokittaja :epaonnistunut lokiviesti (str " Virhe: " error) tapahtuma-id)
           ;; Virhetilanteissa Httpkit ei heitä kiinni otettavia exceptioneja, vaan palauttaa error-objektin.
           ;; Siksi erityyppiset virheet käsitellään instance-tyypin selvittämisellä.
           (cond (or (instance? ConnectException error)
                     (instance? TimeoutException error))
                 (throw+ {:type virheet/+ulkoinen-kasittelyvirhe-koodi+
                          :virheet [{:koodi :ulkoinen-jarjestelma-palautti-virheen :viesti "Ulkoiseen järjestelmään ei saada yhteyttä."}]})
                 :default
                 (throw+ {:type virheet/+ulkoinen-kasittelyvirhe-koodi+
                          :virheet [{:koodi :ulkoinen-jarjestelma-palautti-virheen :viesti "Ulkoisen järjestelmän kommunikoinnissa tapahtui odottaman virhe."}]})))
         (do
           (let [vastausdata (kasittele-vastaus body headers)]
             (log/debug (format "Kutsu palveluun: %s onnistui." url))
             (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti nil tapahtuma-id nil)
             vastausdata)))))))

(defprotocol Http
  (get [lokittaja otsikot parametrit] [lokittaja otsikot parametrit kasittele-vastaus-fn])
  (post [lokittaja otsikot parametrit] [lokittaja otsikot parametrit kasittele-vastaus-fn])
  (head [lokittaja otsikot parametrit] [lokittaja otsikot parametrit kasittele-vastaus-fn]))

(defrecord HttpIntegraatiopiste [url {:keys [salasana kayttajatunnus]}]

  Http
  (get [lokittaja otsikot parametrit]
    (laheta-kutsu lokittaja url :get otsikot parametrit nil nil))

  (get [lokittaja otsikot parametrit kasittele-vastaus-fn]
    (laheta-kutsu lokittaja url :get otsikot parametrit nil kasittele-vastaus-fn))

  (post [lokittaja otsikot parametrit]
    (laheta-kutsu lokittaja url :post otsikot parametrit nil nil))

  (post [lokittaja otsikot parametrit kasittele-vastaus-fn]
    (laheta-kutsu lokittaja url :post otsikot parametrit nil kasittele-vastaus-fn))

  (head [lokittaja otsikot parametrit]
    (laheta-kutsu lokittaja url :head otsikot parametrit nil nil))

  (head [lokittaja otsikot parametrit kasittele-vastaus-fn]
    (laheta-kutsu lokittaja url :head otsikot parametrit nil kasittele-vastaus-fn)) )
