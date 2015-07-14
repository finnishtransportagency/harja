(ns harja.palvelin.integraatiot.api.reittitoteuma
  "Reittitoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.havainnot :as havainnot]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.toteumat :as toteumat]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet] :as liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Reittitoteuma kirjattu onnistuneesti"}]
    vastauksen-data))

(defn luo-reitin-tehtavat [db kirjaaja reittipiste reittipiste-id]
  (log/debug "Luodaan reitin tehtävät")
  (doseq [tehtava (get-in reittipiste [:reittipiste :tehtavat])]
    (toteumat/luo-reitti_tehtava<!
      db
      reittipiste-id
      (get-in tehtava [:tehtava :id])
      (get-in tehtava [:tehtava :maara :maara]))))

(defn luo-reitin-materiaalit [db kirjaaja reittipiste reittipiste-id]
  (log/debug "Luodaan reitin materiaalit")
  (doseq [materiaali (get-in reittipiste [:reittipiste :materiaalit])]
    (let [materiaali-nimi (api-toteuma/materiaali-enum->string (:materiaali materiaali))
          materiaalikoodi-id (:id (first (materiaalit/hae-materiaalikoodin-id-nimella db materiaali-nimi)))]
      (if (nil? materiaalikoodi-id) (throw (RuntimeException. (format "Materiaalia %s ei löydy tietokannasta" materiaali-nimi))))
      (toteumat/luo-reitti_materiaali<!
        db
        reittipiste-id
        materiaalikoodi-id
        (get-in materiaali [:maara :maara])))))

(defn luo-reitti [db kirjaaja reitti toteuma-id]
  (log/debug "Luodaan uusi reittipiste")
  (doseq [reittipiste reitti]
    (let [reittipiste-id (:id (toteumat/luo-reittipiste<!
                                db
                                toteuma-id
                                (pvm-string->java-sql-date (get-in reittipiste [:reittipiste :aika]))
                                (get-in reittipiste [:reittipiste :koordinaatit :x])
                                (get-in reittipiste [:reittipiste :koordinaatit :y])
                                (get-in reittipiste [:reittipiste :koordinaatit :z])))]
      (log/debug "Reittipiste tallennettu, id: " reittipiste-id)
      (log/debug "Aloitetaan reittipisteen tehtävien tallennus.")
      (luo-reitin-tehtavat db kirjaaja reittipiste reittipiste-id)
      (log/debug "Aloitetaan reittipisteen materiaalien tallennus.")
      (luo-reitin-materiaalit db kirjaaja reittipiste reittipiste-id))))


(defn poista-toteuman-reitti [db toteuma-id]
  (log/debug "Selvitetään toteumaan kuuluvat reittipisteet")
  (let [reittipisteet (toteumat/hae-toteuman-reittipisteet-idlla db toteuma-id)]
    (log/debug "Poistetaan reittipisteiden tehtävät & materiaalit")
    (doseq [reittipiste reittipisteet]
      (toteumat/poista-reitti_tehtava-reittipiste-idlla!
        db
        (:id reittipiste))
      (toteumat/poista-reitti_materiaali-reittipiste-idlla!
        db
        (:id reittipiste)))

    (log/debug "Poistetaan reittipisteet")
    (toteumat/poista-reittipiste-toteuma-idlla!
      db
      toteuma-id)))

(defn tallenna-toteuma-ja-reitti [db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
    (let [toteuma (get-in data [:reittitoteuma :toteuma])
          reitti (get-in data [:reittitoteuma :reitti])
          toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma transaktio urakka-id kirjaaja toteuma)]
      (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
      (log/debug "Aloitetaan toteuman tehtävien tallennus")
      (api-toteuma/tallenna-tehtavat transaktio kirjaaja toteuma toteuma-id)
      (log/debug "Aloitetaan toteuman materiaalien tallennus")
      (api-toteuma/tallenna-materiaalit transaktio kirjaaja toteuma toteuma-id)
      (log/debug "Aloitetaan toteuman vanhan reitin (jos sellainen on) poistaminen")
      (poista-toteuman-reitti transaktio toteuma-id)
      (log/debug "Aloitetaan reitin tallennus")
      (luo-reitti transaktio kirjaaja reitti toteuma-id))))

(defn kirjaa-toteuma [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi reittitoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-toteuma-ja-reitti db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

; FIXME Yksi recordi ja reitit mappiin, katso mallia tarkastuksista

(defrecord Reittitoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-reittitoteuma
      (POST "/api/urakat/:id/toteumat/reitti" request
        (kasittele-kutsu db integraatioloki :lisaa-reittitoteuma request skeemat/+reittitoteuman-kirjaus+ skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja] (kirjaa-toteuma db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-pistetoteuma)
    this))
