(ns harja.palvelin.integraatiot.api.urakan-tyotunnit
  "Urakan yleistietojen API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakan-tyotunnit :as q]
            [harja.domain.urakan-tyotunnit :as d]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi])
  (:use [slingshot.slingshot :only [throw+]]))

(defn kolmannes-tallennetavaksi [urakka-id {:keys [vuosi kolmannes tunnit]}]
  {::d/urakka-id urakka-id
   ::d/vuosi vuosi
   ::d/vuosikolmannes kolmannes
   ::d/tyotunnit tunnit})

(defn laheta-turiin [turi urakka-id tyotunnit]
  (future
    (doseq [{:keys [vuosi kolmannes]} tyotunnit]
      (try
        (turi/laheta-urakan-vuosikolmanneksen-tyotunnit turi urakka-id vuosi kolmannes)
        (catch Exception e
          (log/error e (format "Urakan (id: %s) työtuntien (vuosi: %s, kolmannes: %s) lähettäminen epäonnistui"
                               urakka-id vuosi kolmannes)))))))

(defn kirjaa-urakan-tyotunnit [turi db kayttaja {urakka-id :id} {tyotunnit :tyotunnit}]
  (let [urakka-id (Integer/parseInt urakka-id)]
    (log/info (format "Kirjataan urakalle (id: %s) työtunnit käyttäjän (%s) toimesta. Työtunnit: %s."
                      urakka-id
                      kayttaja
                      tyotunnit))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (jdbc/with-db-transaction [db db]
      (doseq [{vuosikolmannes :vuosikolmannes} tyotunnit]
        (q/tallenna-urakan-tyotunnit db (kolmannes-tallennetavaksi urakka-id vuosikolmannes))))

    (laheta-turiin turi urakka-id (map :vuosikolmannes tyotunnit))

    (tee-kirjausvastauksen-body {:ilmoitukset "Työtunnit kirjattu onnistuneesti"})))

(defn palvelut [turi]
  [{:palvelu :kirjaa-urakan-tyotunnit
    :polku "/api/urakat/:id/tyotunnit"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/urakan-tyotuntien-kirjaus-request
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-urakan-tyotunnit turi db kayttaja parametrit data))}])

(defrecord UrakanTyotunnit []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki turi :turi :as this}]
    (palvelut/julkaise http db integraatioloki (palvelut turi))
    this)
  (stop [{http :http-palvelin turi :turi :as this}]
    (palvelut/poista http (palvelut turi))
    this))
