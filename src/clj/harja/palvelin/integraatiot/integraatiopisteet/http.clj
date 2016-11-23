(ns harja.palvelin.integraatiot.integraatiopisteet.http
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [new-reliquary.core :as nr])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.net ConnectException)
           (org.httpkit.client TimeoutException)))

(def timeout-aika-ms 60000)

(defn rakenna-http-kutsu [{:keys [metodi otsikot parametrit kayttajatunnus salasana kutsudata timeout]}]
  (let [kutsu {}]
    (-> kutsu
        (cond-> (not-empty otsikot) (assoc :headers otsikot)
                (not-empty parametrit) (assoc :query-params parametrit)
                (and (not-empty kayttajatunnus)) (assoc :basic-auth [kayttajatunnus salasana])
                (or (= metodi :post) (= metodi :put)) (assoc :body kutsudata)
                timeout (assoc :timeout timeout)))))

(defn tee-http-kutsu [lokittaja tapahtuma-id url metodi otsikot parametrit kayttajatunnus salasana kutsudata]
  (try


    (let [kutsu (rakenna-http-kutsu {:metodi metodi
                                     :otsikot otsikot
                                     :parametrit parametrit
                                     :kayttajatunnus kayttajatunnus
                                     :salasana salasana
                                     :kutsudata kutsudata
                                     :timeout timeout-aika-ms})]
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
      (log/error e (format "HTTP-kutsukäsittelyssä tapahtui poikkeus. (URL: %s)" url))
      (lokittaja :epaonnistunut nil (str " Tapahtui poikkeus: " e) tapahtuma-id)
      (throw+
        {:type virheet/+ulkoinen-kasittelyvirhe-koodi+
         :virheet [{:koodi :poikkeus :viesti (str "HTTP-kutsukäsittelyssä tapahtui odottamaton virhe.")}]}))))

(defn kasittele-virhe [lokittaja lokiviesti tapahtuma-id url error status]
  (log/error (format "Kutsu palveluun: %s epäonnistui. Virhe: %s. Statuskoodi: %s" url error status))
  (log/error "Virhetyyppi: " (type error))
  (lokittaja :epaonnistunut lokiviesti (str "Virhe: " error ", statuskoodi: %s" status) tapahtuma-id)
  ;; Virhetilanteissa Httpkit ei heitä kiinni otettavia exceptioneja, vaan palauttaa error-objektin.
  ;; Siksi erityyppiset virheet käsitellään instance-tyypin selvittämisellä.
  (cond (or (instance? ConnectException error)
            (instance? TimeoutException error))
        (throw+ {:type virheet/+ulkoinen-kasittelyvirhe-koodi+
                 :virheet [{:koodi :ulkoinen-jarjestelma-palautti-virheen
                            :viesti "Ulkoiseen järjestelmään ei saada yhteyttä."}]})
        :default
        (throw+ {:type virheet/+ulkoinen-kasittelyvirhe-koodi+
                 :virheet [{:koodi :ulkoinen-jarjestelma-palautti-virheen :viesti
                            (format "Kommunikoinnissa ulkoisen järjestelmän kanssa tapahtui odottamaton virhe.  Ulkoinen järjestelmä palautti statuskoodin: %s ja virheen: %s." status error)}]})))

(defn kasittele-onnistunut-kutsu [lokittaja lokiviesti tapahtuma-id url body headers]
  (log/debug (format "Kutsu palveluun: %s onnistui." url))
  (lokittaja :onnistunut lokiviesti nil tapahtuma-id)
  {:body body :headers headers})

(defn laheta-kutsu
  [lokittaja tapahtuma-id url metodi otsikot parametrit kayttajatunnus salasana kutsudata]
  ;; todo: instrumentoi new reliciin
  (nr/with-newrelic-transaction
    "HTTP integraatiopiste"
    (str ":http-integraatiopiste-" (lokittaja :avain))
    {:url url
     :metodi metodi
     :otsikot otsikot
     :parametrit parametrit}
    #(do
      (log/debug (format "Lähetetään HTTP %s -kutsu: osoite: %s, metodi: %s, data: %s, otsikkot: %s, parametrit: %s"
                         metodi url metodi kutsudata otsikot parametrit))

      (let [sisaltotyyppi (get otsikot " Content-Type ")]
        (lokittaja :rest-viesti tapahtuma-id "ulos" url sisaltotyyppi kutsudata otsikot (str parametrit))

        (let [{:keys [status body error headers]}
              (tee-http-kutsu lokittaja tapahtuma-id url metodi otsikot parametrit kayttajatunnus salasana kutsudata)
              lokiviesti (integraatioloki/tee-rest-lokiviesti "sisään" url sisaltotyyppi body headers nil)]
          (log/debug (format "Palvelu palautti: tila: %s , otsikot: %s , data: %s" status headers body))

          (if (or error
                  (not (= 200 status)))
            (kasittele-virhe lokittaja lokiviesti tapahtuma-id url error status)
            (kasittele-onnistunut-kutsu lokittaja lokiviesti tapahtuma-id url body headers)))))))

(defprotocol HttpIntegraatiopiste
  (GET
    [this url]
    [this url otsikot parametrit])
  (POST
    [this url kutsudata]
    [this url otsikot parametrit kutsudata])
  (HEAD
    [this url]
    [this url otsikot parametrit]))

(defrecord Http [lokittaja tapahtuma-id asetukset]
  HttpIntegraatiopiste

  (GET [_ url]
    (laheta-kutsu lokittaja tapahtuma-id url :get nil nil (:kayttajatunnus asetukset) (:salasana asetukset) nil))

  (GET [_ url otsikot parametrit]
    (laheta-kutsu lokittaja tapahtuma-id url :get otsikot parametrit (:kayttajatunnus asetukset) (:salasana asetukset) nil))

  (POST [_ url kutsudata]
    (laheta-kutsu lokittaja tapahtuma-id url :post nil nil (:kayttajatunnus asetukset) (:salasana asetukset) kutsudata))

  (POST [_ url otsikot parametrit kutsudata]
    (laheta-kutsu lokittaja tapahtuma-id url :post otsikot parametrit (:kayttajatunnus asetukset) (:salasana asetukset) kutsudata))

  (HEAD [_ url]
    (laheta-kutsu lokittaja tapahtuma-id url :head nil nil (:kayttajatunnus asetukset) (:salasana asetukset) nil))

  (HEAD [_ url otsikot parametrit]
    (laheta-kutsu lokittaja tapahtuma-id url :head otsikot parametrit (:kayttajatunnus asetukset) (:salasana asetukset) nil)))

(defn luo-integraatiopiste
  ([lokittaja tapahtuma-id]
   (luo-integraatiopiste lokittaja tapahtuma-id nil))
  ([lokittaja tapahtuma-id asetukset]
   (->Http lokittaja tapahtuma-id asetukset)))