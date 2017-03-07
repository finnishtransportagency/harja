(ns harja.palvelin.integraatiot.api.tiemerkintatoteuma
  "Pistetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.validointi.toteumat :as toteuman-validointi]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (tee-kirjausvastauksen-body {:ilmoitukset "Pistetoteuma kirjattu onnistuneesti"}))
(defn kirjaa-tiemerkintatoteuma [db kayttaja parametrit data]
  )

(defn poista-tiemerkintatoteuma [db kayttaja parametrit data]
  )

(defn palvelut [liitteiden-hallinta]
  [
   {:palvelu :kirjaa-tiemerkintatoteuma
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
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki liitteiden-hallinta :liitteiden-hallinta :as this}]
    (palvelut/julkaise http db integraatioloki (palvelut liitteiden-hallinta))
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http (palvelut nil))
    this))

