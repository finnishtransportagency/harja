(ns harja.palvelin.integraatiot.labyrintti.sms
  "Labyrintti SMS Gateway"
  (:require [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol Sms
  (rekisteroi-kuuntelija! [this kasittely-fn])
  (laheta [this numero viesti]))

(defn kasittele-vastaus [body headers]
  (log/debug (format "Labyrintin SMS Gateway vastasi: sisältö: %s, otsikot: %s" body headers))
  (when (and body (.contains (string/lower-case body) "error"))
    (throw+ {:type :sms-lahetys-epaonnistui
             :error body}))
  {:sisalto body :otsikot headers})

(defn laheta-sms [db integraatioloki kayttajatunnus salasana url numero viesti]
  (if (or (empty? kayttajatunnus) (empty? salasana) (empty? url))
    (log/warn "Käyttäjätunnusta, salasanaa tai URL Labyrintin SMS Gatewayhyn ei ole annettu. Viestiä ei voida lähettää.")
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "labyrintti" "laheta"
      (fn [konteksti]
        (let [otsikot {"Content-Type" "application/x-www-form-urlencoded"}
              parametrit {"dests" numero
                          "text" viesti}
              http-asetukset {:metodi :GET
                              :url url
                              :kayttajatunnus kayttajatunnus
                              :salasana salasana
                              :otsikot otsikot
                              :parametrit parametrit}
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-vastaus body headers))))))

(defn kasittele-epaonnistunut-viestin-kasittely [integraatioloki tapahtuma-id poikkeus]
  (log/error (format "Tekstiviestin vastaanotossa tapahtui poikkeus." poikkeus))
  (integraatioloki/kirjaa-epaonnistunut-integraatio
    integraatioloki
    "Tekstiviestin vastaanotossa tapahtui poikkeus"
    (.toString poikkeus)
    tapahtuma-id
    nil))

(defn vastaanota-tekstiviesti [integraatioloki kutsu kuuntelijat]
  (log/debug (format "Vastaanotettiin tekstiviesti Labyrintin SMS Gatewaystä: %s" kutsu))
  (let [url (:remote-addr kutsu)
        otsikot (:headers kutsu)
        parametrit (:params kutsu)
        viesti (integraatioloki/tee-rest-lokiviesti "sisään" url nil nil otsikot (str parametrit))
        tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "labyrintti" "vastaanota" nil viesti)
        numero (get parametrit "source")
        viesti (get parametrit "text")]
    (try
      (let [vastaukset (mapv #(% numero viesti) @kuuntelijat)
            vastausdata (if (empty? vastaukset) "" (str "text=" (string/join ", " vastaukset)))
            vastausviesti (integraatioloki/tee-rest-lokiviesti "ulos" url nil vastausdata nil nil)]
        (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki vastausviesti nil tapahtuma-id nil)
        {:status 200
         :body vastausdata
         :headers {"Content-Type" "application/x-www-form-urlencoded"
                   "Content-Length" (count vastausdata)}})
      (catch Exception e
        (kasittele-epaonnistunut-viestin-kasittely integraatioloki tapahtuma-id e)
        {:status 500}))))

(defrecord Labyrintti [url kayttajatunnus salasana kuuntelijat]
  component/Lifecycle
  (start [{http :http-palvelin integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :vastaanota-tekstiviesti
      (POST "/sms" request (vastaanota-tekstiviesti integraatioloki request kuuntelijat))
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
    (laheta-sms (:db this)
                (:integraatioloki this)
                (:kayttajatunnus this)
                (:salasana this)
                (:url this)
                numero
                viesti)))

(defn luo-labyrintti [asetukset]
  (->Labyrintti (:url asetukset) (:kayttajatunnus asetukset) (:salasana asetukset) (atom #{})))

(defrecord FeikkiLabyrintti []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  Sms
  (rekisteroi-kuuntelija! [this kasittelija]
    (log/info "Feikki Labyrintti EI tue kuuntelijan rekisteröintiä")
    #(log/info "Poistetaan Feikki Labyrintin kuuntelija"))
  (laheta [this numero viesti]
    (log/info "Feikki Labyrintti lähettää muka viestin numeroon " numero ": " viesti)))

(defn feikki-labyrintti []
  (->FeikkiLabyrintti))
