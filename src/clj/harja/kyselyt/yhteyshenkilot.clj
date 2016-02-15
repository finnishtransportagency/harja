(ns harja.kyselyt.yhteyshenkilot
  (:require [yesql.core :refer [defqueries]]
            [harja.domain.puhelinnumero :as puhelinnumero]))

(defqueries "harja/kyselyt/yhteyshenkilot.sql")

(defn onko-olemassa-yhteyshenkilo-ulkoisella-idlla? [db ulkoinen-id]
  (:exists (first (onko-olemassa-yhteyshenkilo-ulkoisella-idlla db ulkoinen-id))))

(defn onko-olemassa-paivystys-jossa-yhteyshenkilona-id? [db paivystaja-id]
  (:exists (first (onko-olemassa-paivystys-jossa-yhteyshenkilona-id db paivystaja-id))))

(defn hae-urakan-tamanhetkinen-paivystaja [db urakkaid]
  (let [paivystajat (hae-urakan-taman-hetkiset-paivystajat db urakkaid)]
    (if (= 1 (count paivystajat))
      (first paivystajat)
      (when (< 0 (count paivystajat))
        (if (some :vastuuhenkilo paivystajat)
          (first (filter :vastuuhenkilo paivystajat))
          (first paivystajat))))))

(defn luo-yhteyshenkilo [db etu suku tyopuhelin matkapuhelin email org sampoid kayttajatunnus ulkoinen_id]
  (luo-yhteyshenkilo<!
    db
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
  (paivita-yhteyshenkilo<! db
                           etunimi
                           sukunimi
                           (puhelinnumero/kanonisoi tyopuhelin)
                           (puhelinnumero/kanonisoi matkapuhelin)
                           sahkoposti
                           organisaatio
                           id))

(defn paivita-yhteyshenkilo-ulkoisella-idlla [db etunimi sukunimi tyopuhelin matkapuhelin sahkoposti organisaatio ulkoinen_id]
  (paivita-yhteyshenkilo-ulkoisella-idlla<! db
                                            etunimi
                                            sukunimi
                                            (puhelinnumero/kanonisoi tyopuhelin)
                                            (puhelinnumero/kanonisoi matkapuhelin)
                                            sahkoposti
                                            organisaatio
                                            ulkoinen_id))