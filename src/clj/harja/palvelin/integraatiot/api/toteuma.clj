(ns harja.palvelin.integraatiot.api.toteuma
  "Toteuman kirjaaminen urakalle"
  (:require [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.toteumat :as toteumat]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.validointi.toteumat :as validointi])
  (:use [slingshot.slingshot :only [throw+]]))

(defn hae-toteuman-kaikki-sopimus-idt [toteumatyyppi-yksikko toteumatyyppi-monikko data]
  (keep identity
        (reduce
          conj
          [(get-in data [toteumatyyppi-yksikko :toteuma :sopimusId])]
          (mapv
            #(get-in % [toteumatyyppi-yksikko :toteuma :sopimusId])
            (toteumatyyppi-monikko data)))))


(defn hae-sopimus-id [db urakka-id toteuma]
  (let [sopimus-id (or (:sopimusId toteuma) (:id (first (sopimukset/hae-urakan-paasopimus db urakka-id))))]
    (if sopimus-id
      sopimus-id
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sopimusta-ei-loydy+
                          :viesti (format "Urakalle (id: %s.) ei löydy sopimusta" urakka-id)}]}))))

(defn paivita-toteuma [db urakka-id kirjaaja toteuma]
  (log/debug "Päivitetään vanha toteuma, jonka ulkoinen id on " (get-in toteuma [:tunniste :id]))
  (validointi/validoi-toteuman-pvm-vali (:alkanut toteuma) (:paattynyt toteuma))
  (validointi/tarkista-tehtavat db (:tehtavat toteuma))
  (let [sopimus-id (hae-sopimus-id db urakka-id toteuma)]
    (:id (toteumat/paivita-toteuma-ulkoisella-idlla<!
           db
           (aika-string->java-sql-date (:alkanut toteuma))
           (aika-string->java-sql-date (:paattynyt toteuma))
           (:id kirjaaja)
           (get-in toteuma [:suorittaja :nimi])
           (get-in toteuma [:suorittaja :ytunnus])
           ""
           (:toteumatyyppi toteuma)
           (:reitti toteuma)
           sopimus-id
           (get-in toteuma [:tunniste :id])
           urakka-id))))

(defn luo-uusi-toteuma [db urakka-id kirjaaja toteuma]
  (log/debug "Luodaan uusi toteuma.")
  (validointi/validoi-toteuman-pvm-vali (:alkanut toteuma) (:paattynyt toteuma))
  (validointi/tarkista-tehtavat db (:tehtavat toteuma))
  (let [sopimus-id (hae-sopimus-id db urakka-id toteuma)]
    (:id (toteumat/luo-toteuma<!
           db
           urakka-id
           sopimus-id
           (aika-string->java-sql-date (:alkanut toteuma))
           (aika-string->java-sql-date (:paattynyt toteuma))
           (:toteumatyyppi toteuma)
           (:id kirjaaja)
           (get-in toteuma [:suorittaja :nimi])
           (get-in toteuma [:suorittaja :ytunnus])
           ""
           (get-in toteuma [:tunniste :id])
           (:reitti toteuma)
           nil nil nil nil nil
           "harja-api"))))

(defn paivita-tai-luo-uusi-toteuma [db urakka-id kirjaaja toteuma]
  (if (toteumat/onko-olemassa-ulkoisella-idlla? db (get-in toteuma [:tunniste :id]) (:id kirjaaja))
    (paivita-toteuma db urakka-id kirjaaja toteuma)
    (luo-uusi-toteuma db urakka-id kirjaaja toteuma)))

(defn paivita-toteuman-reitti [db toteuma-id reitti]
  (toteumat/paivita-toteuman-reitti! db {:id toteuma-id
                                         :reitti reitti}))

(defn tallenna-sijainti [db sijainti aika toteuma-id]
  (log/debug "Tuhotaan toteuman " toteuma-id " vanha sijainti")
  (toteumat/poista-reittipiste-toteuma-idlla! db toteuma-id)
  (log/debug "Luodaan toteumalle uusi sijainti reittipisteenä")
  (toteumat/luo-reittipiste<! db toteuma-id aika
                              (get-in sijainti [:koordinaatit :x])
                              (get-in sijainti [:koordinaatit :y])))

(defn tallenna-tehtavat [db kirjaaja toteuma toteuma-id]
  (log/debug "Tuhotaan toteuman vanhat tehtävät")
  (toteumat/poista-toteuma_tehtava-toteuma-idlla!
    db
    toteuma-id)
  (log/debug "Luodaan toteumalle uudet tehtävät")
  (doseq [tehtava (:tehtavat toteuma)]
    (log/debug "Luodaan tehtävä.")
    (toteumat/luo-toteuma_tehtava<!
      db
      toteuma-id
      (get-in tehtava [:tehtava :id])
      (get-in tehtava [:tehtava :maara :maara])
      (:id kirjaaja)
      nil
      nil)))

(defn tallenna-materiaalit [db kirjaaja toteuma toteuma-id]
  (log/debug "Tuhotaan toteuman vanhat materiaalit")
  (toteumat/poista-toteuma-materiaali-toteuma-idlla! db toteuma-id)
  (log/debug "Luodaan toteumalle uudet materiaalit")
  (doseq [materiaali (:materiaalit toteuma)]
    (log/debug "Etsitään materiaalikoodi kannasta.")
    (let [materiaali-nimi (:materiaali materiaali)
          materiaalikoodi-id (:id (first (materiaalit/hae-materiaalikoodin-id-nimella db materiaali-nimi)))]
      (if (nil? materiaalikoodi-id)
        (throw+ {:type virheet/+sisainen-kasittelyvirhe+
                 :virheet [{:koodi virheet/+tuntematon-materiaali+
                            :viesti (format "Tuntematon materiaali: %s." materiaali-nimi)}]}))
      (toteumat/luo-toteuma-materiaali<!
        db
        toteuma-id
        materiaalikoodi-id
        (get-in materiaali [:maara :maara])
        (:id kirjaaja))))
  ;; Päivitä sopimuksen päivän materiaalinkäyttö
  (materiaalit/paivita-sopimuksen-materiaalin-kaytto db (:sopimusId toteuma) (:alkanut toteuma)))
