(ns harja.kyselyt.tielupa-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tielupa :as tielupa]
            [harja.kyselyt.tielupa :as tielupa-q]))

(use-fixtures :each urakkatieto-fixture)

(def testiluvan-ulkoinen-tunniste 666123)

(def testitielupa
  {::tielupa/tienpitoviranomainen-sahkopostiosoite "teijo.tienpitaja@example.com"
   ::tielupa/hakija-osasto "Osasto 123"
   ::tielupa/kohde-postitoimipaikka "Kiiminki"
   ::tielupa/liikenneohjaajan-sahkopostiosoite "lilli.liikenteenohjaaja@example.com"
   ::tielupa/liikenneohjaajan-yhteyshenkilo "Lilli Liikenteenohjaaja"
   ::tielupa/tienpitoviranomainen-puhelinnumero "987-7889087"
   ::tielupa/voimassaolon-alkupvm #inst"2017-02-11T22:00:00.000-00:00"
   ::tielupa/tienpitoviranomainen-yhteyshenkilo "Teijo Tienpitäjä"
   ::tielupa/kunta "Kiiminki"
   ::tielupa/kohde-lahiosoite "Tie 123"
   ::tielupa/liikenneohjaajan-nimi "Liikenneohjaus Oy"
   ::tielupa/paatoksen-diaarinumero "paatos-123"
   ::tielupa/hakija-tyyppi "kotitalous"
   ::tielupa/urakka 4
   ::tielupa/urakoitsija-sahkopostiosoite "yrjana.yhteyshenkilo@example.com"
   ::tielupa/hakija-postinumero "90900"
   ::tielupa/sijainnit [{::tielupa/ajorata 0
                         ::tielupa/tie 20
                         ::tielupa/aosa 3
                         ::tielupa/let 1
                         ::tielupa/puoli 1
                         ::tielupa/aet 1
                         ::tielupa/losa 4
                         ::tielupa/geometria nil
                         ::tielupa/karttapvm #inst"2016-12-31T22:00:00.000-00:00"
                         ::tielupa/kaista 1}]
   ::tielupa/urakoitsija-puhelinnumero "987-7889087"
   ::tielupa/otsikko "Testilupa mainosten pystyttämiseen"
   ::tielupa/hakija-postinosoite "Liitintie 1"
   ::tielupa/urakan-nimi "Oulun alueurakka"
   ::tielupa/ely 12
   ::tielupa/kohde-postinumero "90900"
   ::tielupa/ulkoinen-tunniste testiluvan-ulkoinen-tunniste
   ::tielupa/hakija-maakoodi "FI"
   ::tielupa/saapumispvm #inst"2017-02-11T22:00:00.000-00:00"
   ::tielupa/liikenneohjaajan-puhelinnumero "987-7889087"
   ::tielupa/katselmus-url "https://tilu.fi/123"
   ::tielupa/voimassaolon-loppupvm #inst"2018-02-11T22:00:00.000-00:00"
   ::tielupa/hakija-nimi "Henna Hakija"
   ::tielupa/myontamispvm #inst"2017-02-11T22:00:00.000-00:00"
   ::tielupa/tyyppi :johto-ja-kaapelilupa
   ::tielupa/hakija-sahkopostiosoite "henna.hakija@example.com"
   ::tielupa/hakija-puhelinnumero "987-7889087"
   ::tielupa/tien-nimi "Kuusamontie"
   ::tielupa/urakoitsija-yhteyshenkilo "Yrjänä Yhteyshenkilo"
   ::tielupa/urakoitsija-nimi "Puulaaki Oy"})

(deftest hae-kanavan-toimenpiteet
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        haettu-osasto "Osasto 123"
        vastaus (tielupa-q/hae-tieluvat db {::tielupa/hakija-osasto haettu-osasto})]
    (is (every? #(= haettu-osasto (::tielupa/hakija-osasto %)) vastaus) "Jokainen löytynyt tietue vastaa hakuehtoa")))

(deftest onko-olemassa-ulkoisella-tunnisteella
  (let [db (tietokanta/luo-tietokanta testitietokanta)]
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db nil)))
    (is (true? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 666)))
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 2345)))
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db "foo")))))

(deftest tallenna-tielupa
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        hae-maara #(ffirst (q "select count (id) from tielupa;"))
        maara-alussa (hae-maara)
        maara-luonnin-jalkeen (+ maara-alussa 1)]

    ;; uuden luominen
    (tielupa-q/tallenna-tielupa db testitielupa)
    (is (= (true? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 666123))))
    (is (= maara-luonnin-jalkeen (hae-maara)))

    ;; paivittaminen ulkoisella tunnisteella
    (let [paivitetty-yhteyshenkilo "Teijo 'TESTIMIES' Tienpitäjä"
          paivitettava (assoc testitielupa ::tielupa/tienpitoviranomainen-yhteyshenkilo paivitetty-yhteyshenkilo)
          paivitetty (tielupa-q/hae-ulkoisella-tunnistella db testiluvan-ulkoinen-tunniste)]
      (tielupa-q/tallenna-tielupa db paivitettava)
      (is (= maara-luonnin-jalkeen (hae-maara)))
      (is paivitetty-yhteyshenkilo (::tielupa/tienpitoviranomainen-yhteyshenkilo paivitetty)))

    ;; paivittaminen Harjan id:lla
    (let [id (::tielupa/id (tielupa-q/hae-ulkoisella-tunnistella db testiluvan-ulkoinen-tunniste))
          paivitetty-urakoitsijan-yhteyshenkilo "Yrjänä 'ÜBER' Yhteyshenkilo"
          paivitettava (assoc testitielupa ::tielupa/urakoitsija-yhteyshenkilo paivitetty-urakoitsijan-yhteyshenkilo
                                           ::tielupa/id id)
          paivitetty (tielupa-q/hae-ulkoisella-tunnistella db testiluvan-ulkoinen-tunniste)]
      (tielupa-q/tallenna-tielupa db paivitettava)
      (is (= maara-luonnin-jalkeen (hae-maara)))
      (is paivitetty-urakoitsijan-yhteyshenkilo (::tielupa/urakoitsija-yhteyshenkilo paivitetty))
      (is (not (nil? (::muokkaustiedot/muokattu paivitetty)))))))