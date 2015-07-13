(ns harja.palvelin.integraatiot.api.toteuma
  "Toteuman kirjaaminen urakalle"
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

(defn tallenna-toteuma [db urakka-id kirjaaja toteuma]
  (if (toteumat/onko-olemassa-ulkoisella-idlla? db (get-in toteuma [:tunniste :id]) (:id kirjaaja))
    (do
      (log/debug "Päivitetään vanha toteuma, jonka ulkoinen id on " (get-in toteuma [:tunniste :id]))
      (:id (toteumat/paivita-toteuma-ulkoisella-idlla<!
             db
             (parsi-aika (:alkanut toteuma))
             (parsi-aika (:paattynyt toteuma))
             (:id kirjaaja)
             (get-in toteuma [:suorittaja :nimi])
             (get-in toteuma [:suorittaja :ytunnus])
             ""
             (get-in toteuma [:tunniste :id])
             urakka-id)))
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
             (get-in toteuma [:suorittaja :nimi])
             (get-in toteuma [:suorittaja :ytunnus])
             ""
             (get-in toteuma [:tunniste :id]))))))

(defn tallenna-sijainti [db data toteuma-id]
  (let [{:keys                      [pistetoteuma]
         {:keys [toteuma sijainti]} :pistetoteuma} data]
    (log/debug "Tuhotaan toteuman vanha reittipiste")
    (toteumat/poista-reittipiste-toteuma-idlla!
      db
      toteuma-id)
    (log/debug "Luodaan toteumalle uusi reittipiste")
    (toteumat/luo-reittipiste<!
      db
      toteuma-id
      (parsi-aika (:alkanut toteuma))
      (:x sijainti)
      (:y sijainti)
      (:z sijainti))))

(defn tallenna-tehtavat [db kirjaaja data toteuma-id]
  (let [{:keys                      [pistetoteuma]
         {:keys [toteuma sijainti]} :pistetoteuma} data
        tehtavat (:tehtavat toteuma)]
    (log/debug "Tuhotaan toteuman vanhat tehtävät")
    (toteumat/poista-toteuma_tehtava-toteuma-idlla!
      db
      toteuma-id)
    (log/debug "Luodaan toteumalle uudet tehtävät")
    (doseq [tehtava tehtavat]
      (log/debug "Luodaan tehtävä: " (pr-str tehtava))
      (toteumat/luo-toteuma_tehtava<!
        db
        toteuma-id
        (get-in tehtava [:tehtava :id])
        nil
        (:id kirjaaja)
        nil
        nil))))