(ns harja.kyselyt.yhteyshenkilot
  (:require [jeesql.core :refer [defqueries]]
            [harja.domain.puhelinnumero :as puhelinnumero]))

(defqueries "harja/kyselyt/yhteyshenkilot.sql"
  {:positional? true})

(defn onko-olemassa-yhteyshenkilo-ulkoisella-idlla? [db ulkoinen-id]
  (:exists (first (harja.kyselyt.yhteyshenkilot/onko-olemassa-yhteyshenkilo-ulkoisella-idlla db ulkoinen-id))))

(defn onko-olemassa-paivystys-jossa-yhteyshenkilona-id? [db paivystaja-id]
  (:exists (first (harja.kyselyt.yhteyshenkilot/onko-olemassa-paivystys-jossa-yhteyshenkilona-id db paivystaja-id))))

(defn onko-olemassa-paivystys-ulkoisella-idlla? [db kayttaja-id ulkoinen-id]
  (:exists (first (harja.kyselyt.yhteyshenkilot/onko-olemassa-paivystys-ulkoisella-idlla db kayttaja-id ulkoinen-id))))

(defn hae-urakan-tamanhetkiset-paivystajat
  "Palauttaa urakan tämän hetkiset päivystäjät, jotka ovat vastuuhenkilöitä (heille viesti)"
  [db urakkaid]
  (->> urakkaid
       (hae-urakan-taman-hetkiset-paivystajat db)
       (filter :vastuuhenkilo)))

(defn luo-yhteyshenkilo [db etu suku tyopuhelin matkapuhelin email org sampoid kayttajatunnus ulkoinen_id]
  (harja.kyselyt.yhteyshenkilot/luo-yhteyshenkilo<!
    db
    etu
    suku
    (puhelinnumero/kanonisoi tyopuhelin)
    (puhelinnumero/kanonisoi matkapuhelin)
    email
    org
    sampoid
    kayttajatunnus
    ulkoinen_id))

(defn paivita-yhteyshenkilo [db etunimi sukunimi tyopuhelin matkapuhelin sahkoposti organisaatio id]
  (harja.kyselyt.yhteyshenkilot/paivita-yhteyshenkilo<! db
                           etunimi
                           sukunimi
                           (puhelinnumero/kanonisoi tyopuhelin)
                           (puhelinnumero/kanonisoi matkapuhelin)
                           sahkoposti
                           organisaatio
                           id))
