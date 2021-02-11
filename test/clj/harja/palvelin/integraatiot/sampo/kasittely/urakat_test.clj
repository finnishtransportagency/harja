(ns harja.palvelin.integraatiot.sampo.kasittely.urakat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.urakat :as urakat]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(use-fixtures :once tietokantakomponentti-fixture)

(deftest urakan-tallentuminen
  (tuo-urakka)
  (is (= 1 (count (hae-urakat))) "Luonnin jälkeen urakka löytyy Sampo id:llä.")
  (poista-urakka))

(deftest maanteiden-hoidon-urakan-tallentuminen
  (tuo-maanteiden-hoidon-urakka)
  (is (= 1 (count (hae-urakat))) "Luonnin jälkeen maanteiden hoidon urakka löytyy Sampo id:llä.")
  (poista-urakka))

(deftest urakan-paivittaminen
  (tuo-urakka)
  (tuo-urakka)
  (is (= 1 (count (hae-urakat))) "Tuotaessa sama urakka uudestaan, päivitetään vanhaa eikä luoda uutta.")
  (poista-urakka))

(deftest yhteyshenkilon-sitominen-urakkaan
  (tuo-urakka)
  (is (onko-yhteyshenkilo-sidottu-urakkaan?) "Urakalle löytyy luonnin jälkeen sampoid:llä sidottu yhteyshenkilö.")
  (poista-urakka))

(deftest urakkatyypin-asettaminen
  (tuo-urakka)
  (is (= "hoito" (hae-urakan-tyyppi)) "Urakkatyyppi on asetettu oikein ennen kuin hanke on tuotu.")
  (poista-urakka)

  (tuo-urakka)
  (tuo-hanke)
  (is (= "hoito" (hae-urakan-tyyppi)) "Urakkatyyppi on asetettu oikein kun urakka on tuotu ensin.")
  (poista-urakka)
  (poista-hanke)

  (tuo-hanke)
  (tuo-urakka)
  (is (= "hoito" (hae-urakan-tyyppi)) "Urakkatyyppi on asetettu oikein kun hanke on tuotu ensin.")
  (poista-urakka)
  (poista-hanke))

(deftest hallintayksikon-asettaminen
  (tuo-urakka)
  (is (.contains (hae-urakan-hallintayksikon-nimi) "Pohjois-Pohjanmaa") "Urakan hallintayksiköksi on asetettu Pohjois-Pohjanmaan ELY")
  (poista-urakka))

(deftest alueurakkanumeron-purku
  (let [osat (urakat/pura-alueurakkanro "TESTI" "TYS-0666")]
    (is (= "TYS" (:tyypit osat)) "Tyypit on purettu oikein")
    (is (= "0666" (:alueurakkanro osat)) "Alueurakkanumero on purettu oikein"))

  (let [osat (urakat/pura-alueurakkanro "TESTI" "TYS0666")]
    (is (nil? (:tyypit osat)) "Tyyppiä ei ole päätelty")
    (is (nil? (:alueurakkanro osat)) "Alueurakkanumeroa ei ole otettu"))

  (let [osat (urakat/pura-alueurakkanro "TESTI" "TYS-!0666")]
    (is (= "TYS" (:tyypit osat)) "Tyyppi on päätelty oikein ")
    (is (nil? (:alueurakkanro osat)) "Alueurakkanumeroa ei ole otettu"))

  (let [osat (urakat/pura-alueurakkanro "TESTI" "THS-0666")]
    (is (= "THS" (:tyypit osat)) "Tyyppi on päätelty oikein ")
    (is (= "0666" (:alueurakkanro osat)) "Alueurakkanumero on purettu oikein"))

  (let [osat (urakat/pura-alueurakkanro "TESTI" "T--0FF666")]
    (is (nil? (:tyypit osat)) "Tyyppiä ei ole päätelty")
    (is (nil? (:alueurakkanro osat)) "Alueurakkanumeroa ei ole otettu")))

(deftest harjassa-luodun-urakan-kasittely
  (u "UPDATE urakka SET harjassa_luotu = true WHERE id = (select id from urakka where sampoid = '1242141-OULU2') ;")
  (let [alkuperainen-urakka (first (q "select * from urakka where sampoid = '1242141-OULU2'"))]
    (is alkuperainen-urakka "Lähtötilanteessa löytyy urakka Sampo id:llä")
    (tuo-urakka "1242141-OULU2")
    (let [urakka-lahetyksen-jalkeen (first (q "select * from urakka where sampoid = '1242141-OULU2'"))]
      (is (= alkuperainen-urakka urakka-lahetyksen-jalkeen)
          "Urakan tietoja ei ole päivitetty urakalle, joka on luotu Harjassa"))))

(deftest hallintayksikon-asettaminen
  (let [db (:db jarjestelma)
        pop-ely (ffirst (q "select id from organisaatio where sampo_ely_hash = 'KP981'"))
        merivaylat (ffirst (q "select id from organisaatio where lyhenne = 'MV'"))
        kanavat (ffirst (q "select id from organisaatio where lyhenne = 'KAN'"))]
    (is (= kanavat (urakat/hae-hallintayksikko db nil "vesivayla-kanavien-hoito" "KAN-001")))
    (is (= kanavat (urakat/hae-hallintayksikko db nil "vesivayla-kanavien-korjaus" "KAN-002")))
    (is (= merivaylat (urakat/hae-hallintayksikko db nil "vesivayla-hoito" "VESI-001")))
    (is (= pop-ely (urakat/hae-hallintayksikko db "KP981" "hoito" "TIE-001")))))