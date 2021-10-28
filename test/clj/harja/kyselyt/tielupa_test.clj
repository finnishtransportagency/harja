(ns harja.kyselyt.tielupa-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tielupa :as tielupa]
            [harja.kyselyt.tielupa-kyselyt :as tielupa-q]))

(use-fixtures :each (compose-fixtures urakkatieto-fixture tietokantakomponentti-fixture))

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
   ::tielupa/urakat [4]
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
   ::tielupa/urakoiden-nimet ["Oulu"]
   ::tielupa/ely 12
   ::tielupa/kohde-postinumero "90900"
   ::tielupa/ulkoinen-tunniste testiluvan-ulkoinen-tunniste
   ::tielupa/hakija-maakoodi "FI"
   ::tielupa/saapumispvm #inst"2017-02-11T22:00:00.000-00:00"
   ::tielupa/liikenneohjaajan-puhelinnumero "987-7889087"
   ::tielupa/liite-url "https://liite.tilu.fi/123.pdf"
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

(deftest hae-tieluvat
  (let [db (:db jarjestelma)
        haettu-osasto "Osasto 123"
        vastaus (tielupa-q/hae-tieluvat db {::tielupa/hakija-osasto haettu-osasto})]
    (is (every? #(= haettu-osasto (::tielupa/hakija-osasto %)) vastaus) "Jokainen löytynyt tietue vastaa hakuehtoa")))

(deftest onko-olemassa-ulkoisella-tunnisteella
  (let [db (:db jarjestelma)]
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db nil)))
    (is (true? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 666)))
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 2345)))
    (is (false? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db "foo")))))

(deftest tallenna-tielupa
  (let [db (:db jarjestelma)
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

(deftest tallenna-tielupa-ilman-sijaintia
  (let [db (:db jarjestelma)
        hae-maara #(ffirst (q "select count (id) from tielupa;"))
        maara-alussa (hae-maara)
        maara-luonnin-jalkeen (+ maara-alussa 1)
        testitielupa-ilman-sijaintia (assoc testitielupa ::tielupa/sijainnit [])]
    ;; uuden luominen ilman sijaintia
    (tielupa-q/tallenna-tielupa db testitielupa)
    (is (= (true? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 666123))))
    (is (= maara-luonnin-jalkeen (hae-maara)))
    (is (= (true? (tielupa-q/onko-olemassa-ulkoisella-tunnisteella? db 666123))))
    ))

(deftest filteroi-tieluvat-alueurakan-perusteella
  (let [;; Muokkaa testiluvan tieosoite Ivaloon
        tallennettava (-> testitielupa
                        (assoc-in [::tielupa/sijainnit 0 ::tielupa/tie] 4)
                        (assoc-in [::tielupa/sijainnit 0 ::tielupa/aosa] 554)
                        (assoc-in [::tielupa/sijainnit 0 ::tielupa/aet] 1850)
                        (assoc-in [::tielupa/sijainnit 0 ::tielupa/losa] 553)
                        (assoc-in [::tielupa/sijainnit 0 ::tielupa/let] 100)
                        (assoc ::tielupa/ulkoinen-tunniste 3334))
        _ (tielupa-q/tallenna-tielupa ds tallennettava)

        ;; Käytä osittaista organisaation nimeä, koska testiaineistossa on riskinsä
        oulun-numero (ffirst (q (str "SELECT alueurakkanro FROM alueurakka WHERE nimi like '%Oulu%';")))
        ivalo-numero (ffirst (q (str "SELECT alueurakkanro FROM alueurakka WHERE nimi like '%Ivalo%';")))
        kaikki-tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {})
        oulun-luvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:alueurakkanro oulun-numero})
        ivalon-luvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:alueurakkanro ivalo-numero})]
    ;; Oletuksena, että oulun lupien määrä voi kasvaa, niin ei verrata eksaktia määrää
    (is (> (count kaikki-tieluvat) (count oulun-luvat)))
    ;; Ivalossa on 1 lupa ja tässä testissä lisätään toinen
    (is (= (count ivalon-luvat) 2))))

(deftest hae-tieluvat-tieosoitteen-perusteella-onnistuu
  ;; Käytetään ivalon tielupaa, jonka tiedot on tie=4, aosa=554, aet=1851, losa=553, let=6326
  (testing "Hae pelkällä tie-numerolla"
   (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                           {::tielupa/tie 4}})]
     (is (= (count tieluvat) 1))))
  (testing "Hae pelkällä tie-numerolla - ei löydy"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 5}})]
      (is (= (count tieluvat) 0))))
  (testing "Hae tie-numerolla ja alkuosalla"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 4
                                                             ::tielupa/aosa 554}})]
      (is (= (count tieluvat) 1))))
  (testing "Hae tie-numerolla ja alkuosalla ja alkuosa ei täsmää, mutta on pienempi"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 4
                                                             ::tielupa/aosa 5}})]
      (is (= (count tieluvat) 1))))
  (testing "Hae tie-numerolla ja alkuosalla - kun alkuosa ei täsmää"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 4
                                                             ::tielupa/aosa 555}})]
      (is (= (count tieluvat) 0))))
  (testing "Hae tie-numerolla ja alkuosalla ja alkuetäisyydellä"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 4
                                                             ::tielupa/aosa 554
                                                             ::tielupa/aet 1}})]
      (is (= (count tieluvat) 1))))
  (testing "Hae tie-numerolla ja alkuosalla ja alkuetäisyydellä, kun alkuosa on oikein, mutta alkuetäisyys on liian suuri"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 4
                                                             ::tielupa/aosa 554
                                                             ::tielupa/aet 1852 ; 1851 täsmäisi
                                                             }})]
      (is (= (count tieluvat) 0))))
  (testing "Hae tie-numerolla ja loppuosalla"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 4
                                                             ::tielupa/losa 553}})]
      (is (= (count tieluvat) 1))))
  (testing "Hae tie-numerolla ja alkuosalla ja loppuosalla"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 4
                                                             ::tielupa/aosa 554
                                                             ::tielupa/losa 553}})]
      (is (= (count tieluvat) 1))))
  (testing "Hae tie-numerolla ja alkuosasta ja loppuosasta tehdään väli"
    (let [tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                            {::tielupa/tie 4
                                                             ::tielupa/aosa 1
                                                             ::tielupa/losa 1000}})]
      (is (= (count tieluvat) 1)))))

(deftest hae-yksi-tielupa-onnistuu
  (let [; Hae Ivalon tielupa ensin listaus haulla
        ivalon-lupa (first (tielupa-q/hae-tieluvat-hakunakymaan ds {:harja.domain.tielupa/haettava-tr-osoite
                                                                    {::tielupa/tie 4
                                                                     ::tielupa/aosa 1
                                                                     ::tielupa/losa 1000}}))
        yksittainen-lupa (tielupa-q/hae-tielupa ds (::tielupa/id ivalon-lupa))]
    (is (= (::tielupa/id ivalon-lupa) (::tielupa/id yksittainen-lupa)))))