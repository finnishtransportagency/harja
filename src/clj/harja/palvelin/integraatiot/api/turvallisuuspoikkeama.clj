(ns harja.palvelin.integraatiot.api.turvallisuuspoikkeama
  "Turvallisuuspoikkeaman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.kyselyt.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Kaikki turvallisuuspoikkeamat kirjattu onnistuneesti"}]
    vastauksen-data))

(defn kirjaa-turvallisuuspoikkeama [liitteiden-hallinta db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi turvallisuuspoikkeama urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (log/debug "Sisältö: " (pr-str data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    ; TODO Tallenna
    (tee-onnistunut-vastaus)))

(defrecord Turvallisuuspoikkeama []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-turvallisuuspoikkeama
      (POST "/api/urakat/:id/poikkeamat/turvallisuuspoikkeamat" request
            (kasittele-kutsu db integraatioloki :lisaa-havainto request skeemat/+havainnon-kirjaus+ skeemat/+kirjausvastaus+
                             (fn [parametrit data kayttaja db] (kirjaa-turvallisuuspoikkeama liitteiden-hallinta db parametrit data kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-turvallisuuspoikkeama)
    this))