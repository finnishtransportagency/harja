(ns harja.palvelin.integraatiot.api.tiemerkintatoteuma
  "Pistetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST DELETE]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [harja.palvelin.integraatiot.api.kasittely.tiemerkintatoteumat :as tiemerkintatoteumat])
  (:use [slingshot.slingshot :only [throw+]]))

(defn kirjaa-tiemerkintatoteuma [db kayttaja parametrit data]
  (let [urakka-id (Integer/parseInt (:id parametrit))
        tiemerkintatoteumat (:tiemerkintatoteumat data)]
    (log/info (format "Kirjataan urakalle (id: %s) tiemerkintä toteumia käyttäjän (%s) toimesta. Data: %s."
                 urakka-id
                 kayttaja
                 data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tiemerkintatoteumat/luo-tai-paivita-tiemerkintatoteumat db kayttaja tiemerkintatoteumat)
    (tee-kirjausvastauksen-body {:ilmoitukset "Tiemerkintätoteuma kirjattu onnistuneesti"})))

(defn poista-tiemerkintatoteuma [db kayttaja parametrit data]
  )

(def palvelut
  [{:palvelu :kirjaa-tiemerkintatoteuma
    :polku "/api/urakat/:id/toteumat/tiemerkinta"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/urakan-tiemerkintatoteuman-kirjaus-request
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-tiemerkintatoteuma db kayttaja parametrit data))}
   {:palvelu :poista-tiemerkintatoteuma
    :polku "/api/urakat/:id/toteumat/tiemerkinta"
    :tyyppi :DELETE
    :kutsu-skeema json-skeemat/pistetoteuman-poisto
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (poista-tiemerkintatoteuma db kayttaja parametrit data))}])

(defrecord Tiemerkintatoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))

