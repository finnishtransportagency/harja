(ns harja.palvelin.integraatiot.api.pistetoteuma
  "Pistetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.havainnot :as havainnot]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.toteumat :as toteumat]
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet] :as liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [parsi-aika]]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Pistetoteuma kirjattu onnistuneesti"}]
    vastauksen-data))

(defn tallenna-toteuma [db urakka-id kirjaaja data]
  ; FIXME Ulkoinen id puuttuu, pitää lisätä ja tarkistaa ettei frontti mene rikki
  (let [{:keys                                  [pistetoteuma otsikko]
         {:keys [organisaatio viestintunniste]} :otsikko
         {:keys [toteuma sijainti]}             :pistetoteuma} data]
    (log/debug "Viestitunniste: " viestintunniste)
    (if (toteumat/onko-olemassa-ulkoisella-idlla? db (:id viestintunniste) (:id kirjaaja))
      (do
        (log/debug "Luodaan uusi toteuma.")
        (:id (toteumat/luo-toteuma<!
               db
               urakka-id
               (:sopimusId toteuma)
               (parsi-aika (:alkanut toteuma))
               (parsi-aika (:paattynyt toteuma))
               (:tyyppi toteuma)
               (:id kirjaaja)
               (:nimi organisaatio)
               (:ytunnus organisaatio)
               ""
               (:id viestintunniste))))
      (do
        (log/debug "Päivitetään vanha toteuma, jonka ulkoinen id on " (:id viestintunniste))
        (:id (toteumat/paivita-toteuma!
               db
               (parsi-aika (:alkanut toteuma))
               (parsi-aika (:paattynyt toteuma))
               (:id kirjaaja)
               (:nimi organisaatio)
               (:ytunnus organisaatio)
               ""
               (:id viestintunniste)
               urakka-id))))))

(defn tallenna-sijainti []
  ; FIXME Tuhoa vanha reittipiste ja luo uusi
  )

(defn tallenna-tehtavat []
  ; FIXME Tuhoa vanhat tehtävät ja luo uusi
  )

(defn tallenna [db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
    (let [toteuma-id (tallenna-toteuma transaktio urakka-id kirjaaja data)])
    (tallenna-sijainti)
    (tallenna-tehtavat)))

(defn kirjaa-toteuma [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi pistetoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

(defrecord Pistetoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-pistetoteuma
      (POST "/api/urakat/:id/toteumat/piste" request
        (kasittele-kutsu db integraatioloki :lisaa-pistetoteuma request skeemat/+pistetoteuman-kirjaus+ skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja] (kirjaa-toteuma db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-pistetoteuma)
    this))
