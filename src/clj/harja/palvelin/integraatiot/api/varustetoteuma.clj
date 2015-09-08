(ns harja.palvelin.integraatiot.api.varustetoteuma
  "Varustetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.toteumat :as toteumat]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Varustetoteuma kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tallenna-varuste [db urakka-id kirjaaja {:keys [tunniste tietolaji toimenpide ominaisuudet sijainti
                                               kuntoluokitus piiri]} toteuma-id]
  (log/debug "Luodaan uusi varustetoteuma toteumalle " toteuma-id)
  (toteumat/luo-varustetoteuma<!
         db
         tunniste
         toteuma-id
         toimenpide
         tietolaji
         ominaisuudet
         (get-in sijainti[:tie :numero] )
         (get-in sijainti [:tie :aosa])
         (get-in sijainti [:tie :losa])
         (get-in sijainti [:tie :let])
         (get-in sijainti [:tie :aet])
         piiri
         kuntoluokitus))

(defn tallenna-toteuma [db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
                            (let [toteuma (get-in data [:varustetoteuma :toteuma])
                                  toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma transaktio urakka-id kirjaaja toteuma)
                                  varustetiedot (get-in data [:varustetoteuma :varuste])]
                              (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
                              (log/debug "Aloitetaan sijainnin tallennus")
                              (api-toteuma/tallenna-sijainti transaktio (get-in data [:varustetoteuma :sijainti]) toteuma-id)
                              (log/debug "Aloitetaan toteuman tehtävien tallennus")
                              (api-toteuma/tallenna-tehtavat transaktio kirjaaja toteuma toteuma-id)
                              (log/debug "Aloitetaan toteuman varustetietojen tallentaminen")
                              (tallenna-varuste transaktio urakka-id kirjaaja varustetiedot toteuma-id))))

(defn kirjaa-toteuma [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi varustetoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-toteuma db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

(defrecord Varustetoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-varustetoteuma
      (POST "/api/urakat/:id/toteumat/varuste" request
        (kasittele-kutsu db
                         integraatioloki
                         :lisaa-varustetoteuma
                         request
                         skeemat/+varustetoteuman-kirjaus+
                         skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja db] (kirjaa-toteuma db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-varustetoteuma)
    this))
