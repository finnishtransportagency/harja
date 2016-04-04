(ns harja.kyselyt.yhteyshenkilot
  (:require [jeesql.core :refer [defqueries]]
            [harja.domain.puhelinnumero :as puhelinnumero]))

(defqueries "harja/kyselyt/yhteyshenkilot.sql"
  {:positional? true})

(defn onko-olemassa-yhteyshenkilo-ulkoisella-idlla? [db ulkoinen-id]
  (:exists (first (harja.kyselyt.yhteyshenkilot/onko-olemassa-yhteyshenkilo-ulkoisella-idlla db ulkoinen-id))))

(defn onko-olemassa-paivystys-jossa-yhteyshenkilona-id? [db paivystaja-id]
  (:exists (first (harja.kyselyt.yhteyshenkilot/onko-olemassa-paivystys-jossa-yhteyshenkilona-id db paivystaja-id))))

(defn hae-urakan-tamanhetkinen-paivystaja [db urakkaid]
  (let [paivystajat (harja.kyselyt.yhteyshenkilot/hae-urakan-taman-hetkiset-paivystajat db urakkaid)]
    (if (= 1 (count paivystajat))
      (first paivystajat)
      (when (< 0 (count paivystajat))
        (if (some :vastuuhenkilo paivystajat)
          (first (filter :vastuuhenkilo paivystajat))
          (first paivystajat))))))

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
