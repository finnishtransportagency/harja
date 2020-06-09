(ns harja.kyselyt.paivystajatekstiviestit-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit]))

(defn luo-ilmoitus [ilmoitus-id]
  (u (format "INSERT INTO ilmoitus (ilmoitusid, ilmoitettu, valitetty, vastaanotettu, \"vastaanotettu-alunperin\", otsikko) VALUES (%s, now(), (now() + interval '30 seconds'), (now() + interval '30 seconds') , (now() + interval '30 seconds'), 'yksikkotesti');" ilmoitus-id)))

(defn poista-ilmoitukset []
  (u "DELETE FROM ilmoitus WHERE otsikko = 'yksikkotesti'"))

(defn poista-ilmoitustoimenpiteet []
  (u "DELETE FROM ilmoitustoimenpide WHERE ilmoitus IN (SELECT id FROM ilmoitus WHERE otsikko = 'yksikkotesti')"))

(defn poista-paivystajatekstiviestit [paivystaja-id]
  (u (format "DELETE FROM paivystajatekstiviesti WHERE yhteyshenkilo = %s" paivystaja-id)))

(defn hae-paivystaja []
  (first (q "SELECT id, matkapuhelin FROM yhteyshenkilo WHERE matkapuhelin IS NOT NULL LIMIT 1;")))

(defn sulje-ilmoitus [ilmoitus-id]
  (u (format "INSERT INTO ilmoitustoimenpide (ilmoitus, ilmoitusid, kuittaustyyppi, kuitattu, suunta) VALUES
  ((SELECT id FROM ilmoitus WHERE ilmoitusid = %s), %s,
  'lopetus' , now(), 'sisaan'::viestisuunta);" ilmoitus-id ilmoitus-id)))

(defn hae-seuraava-viestinumero [puhelinnumero]
  (first (first (q (format "SELECT hae_seuraava_vapaa_viestinumero('%s')" puhelinnumero)))))

(deftest tarkista-taman-hetkisen-paivystajan-haku
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        paivystaja (hae-paivystaja)
        paivystaja-id (first paivystaja)
        puhelin (second paivystaja)
        ilmoitus-idt [1111 2222 3333]]

    (doseq [ilmoitus-id ilmoitus-idt] (luo-ilmoitus ilmoitus-id))

    (is (= 1 (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id (nth ilmoitus-idt 0) puhelin))
        "Viestinumero on 1, kun ensimmäinen viesti lähetetään")

    (is (= 2 (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id (nth ilmoitus-idt 1) puhelin))
        "Viestinumero on 2, kun toinen viesti lähetetään")

    (is (= 3 (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id (nth ilmoitus-idt 2) puhelin))
        "Viestinumero on 3, kun kolmas viesti lähetetään")

    (is (= 4 (hae-seuraava-viestinumero puhelin))
        "Seuraava id on 4, kun kolme viestiä on auki")

    (sulje-ilmoitus (nth ilmoitus-idt 1))
    (is (= 4 (hae-seuraava-viestinumero puhelin))
        "Seuraava id on yhä 4, kun välissä tullut ilmoitus on suljettu")

    (sulje-ilmoitus (nth ilmoitus-idt 2))
    (is (= 2 (hae-seuraava-viestinumero puhelin))
        "Seuraava id on yhä 2, kun suurimman id:n omaava ilmoitus on suljettu")

    (poista-ilmoitustoimenpiteet)
    (poista-paivystajatekstiviestit paivystaja-id)
    (poista-ilmoitukset)))

(deftest tarkista-viestinumeron-kierratys
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        paivystaja (hae-paivystaja)
        paivystaja-id (first paivystaja)
        puhelin (second paivystaja)
        ensimmainen-ilmoitus 1111
        toinen-ilmoitus 2222]

    (luo-ilmoitus ensimmainen-ilmoitus)

    (is (= 1 (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id ensimmainen-ilmoitus puhelin))
        "Viestinumero on 1, kun ensimmäinen viesti lähetetään")

    (is (= ensimmainen-ilmoitus (:ilmoitusid (first (paivystajatekstiviestit/hae-puhelin-ja-viestinumerolla db puhelin 1))))
        "Oikea ilmoitus löytyy haulla")

    (sulje-ilmoitus ensimmainen-ilmoitus)

    (luo-ilmoitus toinen-ilmoitus)
    (is (= 1 (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id toinen-ilmoitus puhelin))
        "Viestinumero on 1, kun toinen viesti lähetetään ja ensimmäinen on lopetettu")
    (is (= toinen-ilmoitus (:ilmoitusid (first (paivystajatekstiviestit/hae-puhelin-ja-viestinumerolla db puhelin 1))))
        "Oikea ilmoitus löytyy haulla, kun ensimmäinen ilmoitus on suljettu")

    (poista-ilmoitustoimenpiteet)
    (poista-paivystajatekstiviestit paivystaja-id)
    (poista-ilmoitukset)))


