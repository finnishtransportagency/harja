(ns harja.palvelin.integraatiot.labyrintti.sms
  "Labyrintti SMS Gateway"
  (:require [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol Sms
  (laheta [this numero viesti]))

(defn laheta-sms [integraatioloki kayttajatunnus salasana url numero viesti]
  (if (or (empty? kayttajatunnus) (empty? salasana) (empty? url))
    (log/warn "Käyttäjätunnusta, salasanaa tai URL Labyrintin SMS Gatewayhyn ei ole annettu. Viestiä ei voida lähettää.")
    (let [otsikot {"Content-Type" "application/x-www-form-urlencoded"}
          parametrit {"dests" numero
                      "text"  viesti}]
      (log/debug (format "Lähetetään tekstiviesti numeroon: %s, käyttäen URL: %s. Sisältö: \"%s.\"" numero url viesti))
      (http/laheta-post-kutsu integraatioloki "laheta" "labyrintti" url otsikot parametrit kayttajatunnus salasana nil
                              (fn [body headers]
                                (log/debug (format "Labyrintin SMS Gateway vastasi: sisältö: %s, otsikot: %s" body headers))
                                (when (.contains (string/lower-case body) "error")
                                  (throw+ {:type  :sms-lahetys-epaonnistui
                                           :error body}))
                                {:sisalto body :otsikot headers})))))

(defn vastaanota-tekstiviesti [db integraatioloki kutsu kasittely-fn]
  (spit "/Users/mikkoro/Desktop/sms-viesti.txt" (format "\n\nVastaanotettiin viesti: %s " kutsu) :append true)
  (println (format "Vastaanotettiin viesti: %s " kutsu))


  {:status 200})

(defrecord Labyrintti [url kayttajatunnus salasana]
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :vastaanota-tekstiviesti
      (POST "/sms" request (vastaanota-tekstiviesti db integraatioloki request (fn [numero viesti] (println (str numero viesti)))))
      true)
    (assoc this
      :url url
      :kayttajatunnus kayttajatunnus
      :salasana salasana))

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :vastaanota-tekstiviesti)
    this)

  Sms
  (laheta [this numero viesti]
    (laheta-sms (:integraatioloki this) (:kayttajatunnus this) (:salasana this) (:url this) numero viesti)))