(ns harja.kyselyt.yhteyshenkilot
  (:require [jeesql.core :refer [defqueries]]
            [harja.domain.puhelinnumero :as puhelinnumero]))

(defqueries "harja/kyselyt/yhteyshenkilot.sql"
  {:positional? true})

(declare onko-olemassa-yhteyshenkilo-ulkoisella-idlla onko-olemassa-paivystys-jossa-yhteyshenkilona-id
  onko-olemassa-paivystys-ulkoisella-idlla hae-urakan-taman-hetkiset-paivystajat
  luo-yhteyshenkilo<! paivita-yhteyshenkilo<! liita-yhteyshenkilo-urakkaan<!
  hae-kaynissa-olevien-urakoiden-paivystykset hae-urakat-paivystystarkistukseen
  poista-paivystaja! luo-paivystys<! paivita-paivystys!
  hae-paivystyksen-yhteyshenkilo-id hae-paivystyksen-alkupvm-idlla
  hae-urakan-vastuuhenkilot luo-urakan-vastuuhenkilo<! poista-urakan-vastuuhenkilot-roolille!
  hae-urakan-yleinen-puh-ja-sposti liita-sampon-yhteyshenkilo-urakkaan<! irrota-sampon-yhteyshenkilot-urakalta!
  onko-yhteyshenkilo-liitetty-muualle poista-yhteyshenkilo-idlla!)

(defn onko-olemassa-yhteyshenkilo-ulkoisella-idlla? [db ulkoinen-id]
  (:exists (first (onko-olemassa-yhteyshenkilo-ulkoisella-idlla db ulkoinen-id))))

(defn onko-olemassa-paivystys-ulkoisella-idlla? [db urakka-id ulkoinen-id]
  (:exists (first (onko-olemassa-paivystys-ulkoisella-idlla db ulkoinen-id urakka-id))))

(defn hae-urakan-tamanhetkiset-paivystajat
  "Palauttaa urakan tämän hetkiset päivystäjät"
  [db urakkaid]
  (->> urakkaid
    (hae-urakan-taman-hetkiset-paivystajat db)))

(defn luo-yhteyshenkilo [db etu suku tyopuhelin matkapuhelin email org sampoid kayttajatunnus ulkoinen_id luoja]
  (luo-yhteyshenkilo<!
    db
    etu
    suku
    (puhelinnumero/kanonisoi tyopuhelin)
    (puhelinnumero/kanonisoi matkapuhelin)
    email
    org
    sampoid
    kayttajatunnus
    ulkoinen_id
    luoja))

(defn paivita-yhteyshenkilo [db etunimi sukunimi tyopuhelin matkapuhelin sahkoposti organisaatio id]
  (paivita-yhteyshenkilo<! db
    etunimi
    sukunimi
    (puhelinnumero/kanonisoi tyopuhelin)
    (puhelinnumero/kanonisoi matkapuhelin)
    sahkoposti
    organisaatio
    id))
