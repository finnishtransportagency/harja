(ns harja.palvelin.integraatiot.api.pistetoteuma
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
            [harja.palvelin.integraatiot.api.validointi.toteumat :as toteuman-validointi])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (tee-kirjausvastauksen-body {:ilmoitukset "Pistetoteuma kirjattu onnistuneesti"}))

(defn tallenna-yksittainen-pistetoteuma [db urakka-id kirjaaja {:keys [toteuma sijainti tyokone]}]
  (log/debug "Käsitellään yksittäinen pistetoteuma tunnisteella " (get-in toteuma [:tunniste :id]))
  (let [toteuma (assoc toteuma :reitti nil)
        toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma db urakka-id kirjaaja toteuma tyokone)
        aika (aika-string->java-sql-date (:alkanut toteuma))]
    (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
    (log/debug "Aloitetaan sijainnin tallennus")
    (api-toteuma/tallenna-sijainti db sijainti aika toteuma-id)
    (log/debug "Aloitetaan toteuman tehtävien tallennus")
    (api-toteuma/tallenna-tehtavat db kirjaaja toteuma toteuma-id urakka-id)))

(defn tallenna-kaikki-pyynnon-pistetoteumat [db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [db db]
    (when (:pistetoteuma data)
      (tallenna-yksittainen-pistetoteuma db urakka-id kirjaaja (:pistetoteuma data)))
    (doseq [pistetoteuma (:pistetoteumat data)]
      (tallenna-yksittainen-pistetoteuma db urakka-id kirjaaja (:pistetoteuma pistetoteuma)))))

(defn tarkista-pyynto [db urakka-id kirjaaja data]
  (let [sopimus-idt (api-toteuma/hae-toteuman-kaikki-sopimus-idt :pistetoteuma :pistetoteumat data)]
    (doseq [sopimus-id sopimus-idt]
      (validointi/tarkista-urakka-sopimus-ja-kayttaja db urakka-id sopimus-id kirjaaja)))
  (when (:pistetoteuma data)
    (toteuman-validointi/tarkista-tehtavat
      db
      urakka-id
      (get-in data [:pistetoteuma :toteuma :tehtavat])
      (get-in data [:pistetoteuma :toteuma :toteumatyyppi])))
  (doseq [pistetoteuma (:pistetoteumat data)]
    (toteuman-validointi/tarkista-tehtavat
      db
      urakka-id
      (get-in pistetoteuma [:pistetoteuma :toteuma :tehtavat])
      (get-in pistetoteuma [:pistetoteuma :toteuma :toteumatyyppi]))))

(defn kirjaa-toteuma [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi pistetoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (tarkista-pyynto db urakka-id kirjaaja data)
    (tallenna-kaikki-pyynnon-pistetoteumat db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

(defn poista-toteuma [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)
        ulkoiset-idt (-> data :toteumien-tunnisteet)]
    (log/debug "Poistetaan pistetoteumat jokilla id:t:" ulkoiset-idt "urakalta id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja)
               " (id:" (:id kirjaaja) " tekemänä")
    (tarkista-pyynto db urakka-id kirjaaja data)
    (api-toteuma/poista-toteumat db kirjaaja ulkoiset-idt urakka-id)))

(defrecord Pistetoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-pistetoteuma
      (POST "/api/urakat/:id/toteumat/piste" request
        (kasittele-kutsu db integraatioloki :lisaa-pistetoteuma request json-skeemat/pistetoteuman-kirjaus json-skeemat/kirjausvastaus
                         (fn [parametit data kayttaja db] (kirjaa-toteuma db parametit data kayttaja)))))
    (julkaise-reitti
      http :poista-pistetoteuma
      (DELETE "/api/urakat/:id/toteumat/piste" request
        (kasittele-kutsu db integraatioloki :poista-pistetoteuma request json-skeemat/pistetoteuman-poisto json-skeemat/kirjausvastaus
                         (fn [parametit data kayttaja db] (poista-toteuma db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-pistetoteuma)
    this))
