(ns harja.palvelin.integraatiot.api.urakan_tyotunnit
  "Urakan yleistietojen API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakan-tyotunnit :as q]
            [harja.domain.urakan-tyotunnit :as d]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn kolmannes-tallennetavaksi [urakka-id {:keys [vuosi kolmannes tunnit]}]
  {::d/urakka-id urakka-id
   ::d/vuosi vuosi
   ::d/vuosikolmannes kolmannes
   ::d/tyotunnit tunnit})

(defn kirjaa-urakan-tyotunnit [db kayttaja {urakka-id :id} {tyotunnit :tyotunnit}]
  (log/info (format "Kirjataan urakalle (id: %s) työtunnit käyttäjän (%s) toimesta. Työtunnit: %s."
                    urakka-id
                    kayttaja
                    tyotunnit))
  (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
  (jdbc/with-db-transaction [db db]
    (doseq [{vuosikolmannes :vuosikolmannes} tyotunnit]
      (q/tallenna-urakan-tyotunnit db (kolmannes-tallennetavaksi urakka-id vuosikolmannes))))

  ;; todo: lähetä turiin

  (tee-kirjausvastauksen-body {:ilmoitukset "Työtunnit kirjattu onnistuneesti"}))

(def palvelut
  [{:palvelu :kirjaa-urakan-tyotunnit
    :polku "/api/urakat/:id/tyotunnit"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/urakan-tyotuntien-kirjaus-request
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-urakan-tyotunnit db kayttaja parametrit data))}])

(defrecord UrakanTyotunnit []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))