(ns harja.palvelin.integraatiot.api.pistetoteuma
  "Pistetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Pistetoteuma kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tallenna-toteuma [db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
    (let [toteuma (get-in data [:pistetoteuma :toteuma])
          tunniste (api-toteuma/luo-toteuman-tunniste (get-in data [:otsikko :lahettaja :jarjestelma]) (get-in data [:pistetoteuma :toteuma :tunniste :id]))
          toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma transaktio urakka-id kirjaaja tunniste toteuma)]
      (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
      (log/debug "Aloitetaan sijainnin tallennus")
      (api-toteuma/tallenna-sijainti transaktio (get-in data [:pistetoteuma :sijainti]) toteuma-id)
      (log/debug "Aloitetaan toteuman tehtävien tallennus")
      (api-toteuma/tallenna-tehtavat transaktio kirjaaja toteuma toteuma-id))))

(defn kirjaa-toteuma [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi pistetoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-toteuma db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

(defrecord Pistetoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-pistetoteuma
      (POST "/api/urakat/:id/toteumat/piste" request
        (kasittele-kutsu db integraatioloki :lisaa-pistetoteuma request skeemat/+pistetoteuman-kirjaus+ skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja db] (kirjaa-toteuma db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-pistetoteuma)
    this))
