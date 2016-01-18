(ns harja.palvelin.integraatiot.labyrintti.sms
  "Labyrintti SMS Gateway"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]))

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
                                {:sisalto body :otsikot headers})))))

(defrecord Labyrintti [url kayttajatunnus salasana]
  component/Lifecycle
  (start [this]
    (-> this
        (assoc :url url)
        (assoc :kayttajatunnus kayttajatunnus)
        (assoc :salasana salasana))
    this)
  (stop [this]
    this)

  Sms
  (laheta [this numero viesti]
    (laheta-sms (:integraatioloki this) (:kayttajatunnus this) (:salasana this) (:url this) numero viesti)))