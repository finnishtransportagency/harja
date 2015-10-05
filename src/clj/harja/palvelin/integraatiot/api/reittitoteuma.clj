(ns harja.palvelin.integraatiot.api.reittitoteuma
  "Reittitoteuman kirjaaminen urakalle"
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
            [harja.kyselyt.tieverkko :as tieverkko]
            [clojure.java.jdbc :as jdbc]
            [harja.geo :as geo])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (org.postgresql.util PSQLException)))

(defn- yhdista-viivat [viivat]
  {:type   :line
   :points (mapcat
             (fn [viiva]
               (if (= :line (:type viiva))
                 (:points viiva)
                 (mapcat :points (:lines viiva))))
             viivat)})

(defn- piste [pistepari]
  [(get-in pistepari [:reittipiste :koordinaatit :x])
   (get-in pistepari [:reittipiste :koordinaatit :y])])

(defn- hae-reitti [db [[x1 y1] [x2 y2]]]
  (try
    (geo/pg->clj (:geometria (first (tieverkko/hae-tr-osoite-valille db x1 y1 x2 y2 250))))
    (catch PSQLException e
      (log/warn "Reittitoteuman pisteillä (x1:" x1 " y1: " y1 " & x2: " x2 " y2: " y2 " ) ei ole yhteistä tietä: " e)
      [[x1 y1] [x2 y2]])))

(defn luo-reitti-geometria [db reitti]
  (->> reitti
       (sort-by (comp :aika :reittipiste))
       (map piste)
       (partition 2 1)
       (map #(hae-reitti db %))
       yhdista-viivat
       geo/clj->pg geo/geometry))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Reittitoteuma kirjattu onnistuneesti"}]
    vastauksen-data))

(defn luo-reitin-tehtavat [db reittipiste reittipiste-id]
  (log/debug "Luodaan reitin tehtävät")
  (doseq [tehtava (get-in reittipiste [:reittipiste :tehtavat])]
    (toteumat/luo-reitti_tehtava<!
      db
      reittipiste-id
      (get-in tehtava [:tehtava :id])
      (get-in tehtava [:tehtava :maara :maara]))))

(defn luo-reitin-materiaalit [db reittipiste reittipiste-id]
  (log/debug "Luodaan reitin materiaalit")
  (doseq [materiaali (get-in reittipiste [:reittipiste :materiaalit])]
    (let [materiaali-nimi (api-toteuma/materiaali-enum->string (:materiaali materiaali))
          materiaalikoodi-id (:id (first (materiaalit/hae-materiaalikoodin-id-nimella db materiaali-nimi)))]
      (if (nil? materiaalikoodi-id)
        (throw (RuntimeException. (format "Materiaalia %s ei löydy tietokannasta" materiaali-nimi))))
      (toteumat/luo-reitti_materiaali<! db reittipiste-id materiaalikoodi-id (get-in materiaali [:maara :maara])))))

(defn luo-reitti [db reitti toteuma-id]
  (log/debug "Luodaan uusi reittipiste")
  (doseq [reittipiste reitti]
    (let [reittipiste-id (:id (toteumat/luo-reittipiste<!
                                db
                                toteuma-id
                                (pvm-string->java-sql-date (get-in reittipiste [:reittipiste :aika]))
                                (get-in reittipiste [:reittipiste :koordinaatit :x])
                                (get-in reittipiste [:reittipiste :koordinaatit :y])))]
      (log/debug "Reittipiste tallennettu, id: " reittipiste-id)
      (log/debug "Aloitetaan reittipisteen tehtävien tallennus.")
      (luo-reitin-tehtavat db reittipiste reittipiste-id)
      (log/debug "Aloitetaan reittipisteen materiaalien tallennus.")
      (luo-reitin-materiaalit db reittipiste reittipiste-id))))

(defn poista-toteuman-reitti [db toteuma-id]
  (log/debug "Selvitetään toteumaan kuuluvat reittipisteet")
  (let [reittipisteet (toteumat/hae-toteuman-reittipisteet-idlla db toteuma-id)]
    (log/debug "Poistetaan reittipisteiden tehtävät & materiaalit")
    (doseq [reittipiste reittipisteet]
      (toteumat/poista-reitti_tehtava-reittipiste-idlla! db (:id reittipiste))
      (toteumat/poista-reitti_materiaali-reittipiste-idlla! db (:id reittipiste)))

    (log/debug "Poistetaan reittipisteet")
    (toteumat/poista-reittipiste-toteuma-idlla! db toteuma-id)))

(defn tallenna-toteuma-ja-reitti [db urakka-id kirjaaja data]
  (jdbc/with-db-transaction [transaktio db]
    (let [reitti (get-in data [:reittitoteuma :reitti])
          toteuma (get-in data [:reittitoteuma :toteuma])
          toteuma (assoc toteuma :reitti (luo-reitti-geometria db reitti))
          toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma transaktio urakka-id kirjaaja toteuma)]
      (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
      (log/debug "Aloitetaan toteuman tehtävien tallennus")
      (api-toteuma/tallenna-tehtavat transaktio kirjaaja toteuma toteuma-id)
      (log/debug "Aloitetaan toteuman materiaalien tallennus")
      (api-toteuma/tallenna-materiaalit transaktio kirjaaja toteuma toteuma-id)
      (log/debug "Aloitetaan toteuman vanhan reitin poistaminen, jos sellainen on")
      (poista-toteuman-reitti transaktio toteuma-id)
      (log/debug "Aloitetaan reitin tallennus")
      (luo-reitti transaktio reitti toteuma-id))))

(defn kirjaa-toteuma [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan reittitoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja)
               " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-toteuma-ja-reitti db urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))

(defrecord Reittitoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-reittitoteuma
      (POST "/api/urakat/:id/toteumat/reitti" request
        (kasittele-kutsu db
                         integraatioloki
                         :lisaa-reittitoteuma
                         request
                         skeemat/+reittitoteuman-kirjaus+
                         skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja db]
                           (kirjaa-toteuma db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-reittitoteuma)
    this))
