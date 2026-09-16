(ns harja.palvelin.integraatiot.sampo.kasittely.urakat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.sampo.tyokalut :refer :all]
            [harja.palvelin.integraatiot.sampo.kasittely.urakat :as urakat]
            [harja.kyselyt.organisaatiot :as organisaatiot-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]
            [harja.kyselyt.toimenkuvat-kyselyt :as toimenkuvat-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))

(use-fixtures :once tietokantakomponentti-fixture)

(deftest urakan-tallentuminen
  (tuo-urakka)
  (is (= 1 (count (hae-urakat))) "Luonnin jälkeen urakka löytyy Sampo id:llä.")
  (poista-urakka))

(deftest maanteiden-hoidon-urakan-tallentuminen
  (let [_ (tuo-maanteiden-hoidon-urakka)
        urakat (hae-urakat)
        urakkaid (ffirst urakat)
        urakan-tiedot (first (urakat-q/hae-urakka (:db jarjestelma) {:id urakkaid}))
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        urakan-toimenkuvat (toimenkuvat-kyselyt/hae-urakan-toimenkuvat (:db jarjestelma) {:urakkaid urakkaid})]
    (is (= 1 (count (hae-urakat))) "Luonnin jälkeen maanteiden hoidon urakka löytyy Sampo id:llä.")
    (is (= 7 (count urakan-toimenkuvat)))
    (is (= 0.08M (:lupauspaatoksen_bonusprosentti urakan-parametrit)))
    (is (= #inst "2030-09-29T21:00:00.000-00:00" (:loppupvm urakan-tiedot)))
    (poista-urakka)))

(deftest maanteiden-hoidon-urakan-laskutusraja-true
  (let [_ (tuo-urakka)
        urakat (hae-urakat)
        urakkaid (ffirst urakat)
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakkaid}))
        _ (poista-urakka)

        _ (tuo-maanteiden-hoidon-urakka {:sampo-id nil :ennen-2025? false})
        maanteiden-urakat (hae-urakat)
        teidenhoitourakkaid (ffirst maanteiden-urakat)
        teidenhoitourakan-parametrit (first (urakat-q/hae-urakan-parametrit (:db jarjestelma) {:urakkaid teidenhoitourakkaid}))
        teidenhoitourakan-tiedot (first (urakat-q/hae-urakan-tiedot (:db jarjestelma) {:id teidenhoitourakkaid}))
        _ (poista-urakka)

        _ (tuo-maanteiden-hoidon-urakka {:sampo-id nil :ennen-2025? true})
        maanteiden-urakat-ennen-2025 (hae-urakat)
        teidenhoitourakkaid-ennen-2025 (ffirst maanteiden-urakat-ennen-2025)
        teidenhoitourakan-parametrit-ennen-2025 (first (urakat-q/hae-urakan-parametrit (:db jarjestelma) {:urakkaid teidenhoitourakkaid-ennen-2025}))
        _ (poista-urakka)]
    (is (= "teiden-hoito" (:tyyppi teidenhoitourakan-tiedot)))
    (is (true? (:laskutusraja_kaytossa teidenhoitourakan-parametrit)))
    (is (false? (:laskutusraja_kaytossa teidenhoitourakan-parametrit-ennen-2025)))
    (is (nil? (:laskutusraja_kaytossa urakan-parametrit)))))

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
  (is (.contains (hae-urakan-hallintayksikon-nimi) "Pohjois-Suomi") "Urakan elinvoimakeskukseksi on asetettu Pohjois-Suomi")
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
    (is (nil? (:alueurakkanro osat)) "Alueurakkanumeroa ei ole otettu"))

  ;; Alueurakkanumero voi olla myös sampo-id ainakin valaistusurakoiden tapauksessa
  (let [osat (urakat/pura-alueurakkanro "TESTI" "TYS-PR00051720")]
    (is (= "TYS" (:tyypit osat)))
    (is (= "PR00051720" (:alueurakkanro osat))))

  (let [osat (urakat/pura-alueurakkanro "TESTI" "TYS-!PR00051720")]
    (is (= "TYS" (:tyypit osat)))
    (is (nil? (:alueurakkanro osat))))

  (let [osat (urakat/pura-alueurakkanro "TESTI" "TYS--PR00051720")]
    (is (nil? (:tyypit osat)))
    (is (nil? (:alueurakkanro osat))))

  (let [osat (urakat/pura-alueurakkanro "TESTI" "TYSPR00051720")]
    (is (nil? (:tyypit osat)))
    (is (nil? (:alueurakkanro osat)))))

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
        pop-ely (ffirst (q "select id from organisaatio where elinvoimakeskusnumero = '380048'"))
        merivaylat (ffirst (q "select id from organisaatio where lyhenne = 'MV'"))
        kanavat (ffirst (q "select id from organisaatio where lyhenne = 'KAN'"))]
    (is (= kanavat (urakat/hae-hallintayksikko db nil "vesivayla-kanavien-hoito" "KAN-001")))
    (is (= kanavat (urakat/hae-hallintayksikko db nil "vesivayla-kanavien-korjaus" "KAN-002")))
    (is (= merivaylat (urakat/hae-hallintayksikko db nil "vesivayla-hoito" "VESI-001")))
    (is (= pop-ely (urakat/hae-hallintayksikko db "3800481310" "hoito" "TIE-001")))))

