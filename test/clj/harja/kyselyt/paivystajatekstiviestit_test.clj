(ns harja.kyselyt.paivystajatekstiviestit-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit]))

(defn luo-ilmoitus [ilmoitus-id]
  (u (format "INSERT INTO ilmoitus (ilmoitusid, ilmoitettu, otsikko) VALUES (%s, now(), 'yksikkotesti');" ilmoitus-id)))

(defn poista-ilmoitukset []
  (u "DELETE FROM ilmoitus WHERE otsikko = 'yksikkotesti'"))

(defn poista-paivystajatekstiviestit [paivystaja-id]
  (u (format "DELETE FROM paivystajatekstiviesti WHERE yhteyshenkilo = %s" paivystaja-id)))

(defn hae-paivystaja-id []
  (first (first (q "SELECT id FROM yhteyshenkilo LIMIT 1;"))))

(defn sulje-ilmoitus [ilmoitus-id]
  (u (format "UPDATE ilmoitus SET suljettu = TRUE WHERE ilmoitusid = %s;" ilmoitus-id)))

(defn hae-seuraava-viestinumero [paivystaja-id]
  (first(first(q (format "SELECT hae_seuraava_vapaa_viestinumero(%s);" paivystaja-id)))))

(deftest tarkista-taman-hetkisen-paivystajan-haku
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        paivystaja-id (hae-paivystaja-id)
        ilmoitus-idt [1111 2222 3333]]

    (doseq [ilmoitus-id ilmoitus-idt] (luo-ilmoitus ilmoitus-id))

    (is (= 1 (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id (nth ilmoitus-idt 0)))
        "Viestinumero on 1, kun ensimmäinen viesti lähetetään")

    (is (= 2 (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id (nth ilmoitus-idt 1)))
        "Viestinumero on 2, kun toinen viesti lähetetään")

    (is (= 3 (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id (nth ilmoitus-idt 2)))
        "Viestinumero on 3, kun kolmas viesti lähetetään")

    (is (= 4 (hae-seuraava-viestinumero paivystaja-id))
        "Seuraava id on 4, kun kolme viestiä on auki")

    (sulje-ilmoitus (nth ilmoitus-idt 1))
    (is (= 4 (hae-seuraava-viestinumero paivystaja-id))
        "Seuraava id on yhä 4, kun välissä tullut ilmoitus on suljettu")

    (sulje-ilmoitus (nth ilmoitus-idt 2))
    (is (= 2 (hae-seuraava-viestinumero paivystaja-id))
        "Seuraava id on yhä 2, kun suurimman id:n omaava ilmoitus on suljettu")

    (poista-paivystajatekstiviestit paivystaja-id)
    (poista-ilmoitukset)))
