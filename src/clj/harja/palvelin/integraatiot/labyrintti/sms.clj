(ns harja.palvelin.integraatiot.labyrintti.sms
  "Labyrintti SMS Gateway"
  (:require [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol Sms
  (rekisteroi-kuuntelija! [this kasittely-fn])
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

(defn vastaanota-tekstiviesti [db integraatioloki kutsu kuuntelijat]
  (log/debug (format "Vastaanotettiin tekstiviesti Labyrintin SMS Gatewaystä: %s" kutsu))
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "labyrintti" "vastaanota" nil nil)
        url (:remote-addr kutsu)
        otsikot (:headers kutsu)
        parametrit (:params kutsu)
        _ (integraatioloki/kirjaa-rest-viesti integraatioloki tapahtuma-id "sisään" url nil nil otsikot (str parametrit))
        numero (get parametrit "source")
        viesti (get parametrit "text")]
    (try
      (doseq [kuuntelija @kuuntelijat]
        (kuuntelija numero viesti))
      (catch Exception e
        (log/error (format "Tekstiviestin vastaanotossa tapahtui poikkeus." e))
        (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki "Tekstiveistin vastaanotossa tapahtui poikkeus" (.toString e) tapahtuma-id nil)
        {:status 500}))
    (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki "Tekstiviesti käsitelty onnistuneesti" nil tapahtuma-id nil)
    {:status 200}))

(defrecord Labyrintti [url kayttajatunnus salasana kuuntelijat]
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :vastaanota-tekstiviesti
      (POST "/sms" request (vastaanota-tekstiviesti db integraatioloki request kuuntelijat))
      true)
    (assoc this
      :url url
      :kayttajatunnus kayttajatunnus
      :salasana salasana))

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :vastaanota-tekstiviesti)
    this)

  Sms
  (rekisteroi-kuuntelija! [this kuuntelija-fn]
    (swap! kuuntelijat conj kuuntelija-fn)
    #(swap! kuuntelijat disj kuuntelija-fn))

  (laheta [this numero viesti]
    (laheta-sms (:integraatioloki this) (:kayttajatunnus this) (:salasana this) (:url this) numero viesti)))

(defn luo-labyrintti [asetukset]
  (->Labyrintti (:url asetukset) (:kayttajatunnus asetukset) (:salasana asetukset) (atom #{})))