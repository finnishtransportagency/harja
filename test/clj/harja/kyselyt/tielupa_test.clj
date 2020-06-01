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

(deftest tallenna-tielupa-ilman-sijaintia
  (let [db (tietokanta/luo-tietokanta testitietokanta)
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

(deftest sama-tie?
  (is (true? (tielupa-q/sama-tie? 10 {::tielupa/tie 10})))

  (is (false? (tielupa-q/sama-tie? 0 {::tielupa/tie 10})))
  (is (false? (tielupa-q/sama-tie? 10 {::tielupa/tie nil})))
  (is (false? (tielupa-q/sama-tie? 10 {::tielupa/foobar 10}))))

(deftest tr-piste-aiemmin-tai-sama?
  (is (true? (tielupa-q/tr-piste-aiemmin-tai-sama? 1 1 2 1))
      "1/1 pitäisi olla ennen pistettä 2/1")
  (is (true? (tielupa-q/tr-piste-aiemmin-tai-sama? 1 1 1 2))
      "1/1 on ennen 1/2")
  (is (true? (tielupa-q/tr-piste-aiemmin-tai-sama? 1 1 1 1))
      "Pisteet ovat samat")

  (is (false? (tielupa-q/tr-piste-aiemmin-tai-sama? 2 1 1 1))
      "2/1 ei ole ennen 1/1")
  (is (false? (tielupa-q/tr-piste-aiemmin-tai-sama? 1 2 1 1))
      "1/2 ei ole ennen 1/1"))

(deftest valilla?
  (let [tielupa (fn [aosa aet losa let] {::tielupa/aosa aosa
                                         ::tielupa/aet aet
                                         ::tielupa/losa losa
                                         ::tielupa/let let})]
    (testing "Päällekkäisyys tunnistetaan, kun molemmat ovat välimatkoja"
      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 2 1 3 1)))
          "Tielupa on hakuehtojen välissä")
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 2 1 3 1)))
          "Tielupa on hakuehtojen välissä")

      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 2 1 10 1))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 2 1 10 1))))

      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 0 1 3 1))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 0 1 3 1))))

      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 0 1 10 1))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 0 1 10 1))))

      (is (true? (tielupa-q/valilla?
                   [1 1] [1 100] (tielupa 1 10 1 50))))

      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 4 1 10 1))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 4 1 10 1))))
      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 0 1 1 1))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 0 1 1 1))))

      (is (false? (tielupa-q/valilla?
                    [1 1] [4 1] (tielupa 0 1 0 100))))
      (is (false? (tielupa-q/valilla?
                    [4 1] [1 1] (tielupa 10 1 30 1)))))

    (testing "Päällekkäisyys tunnistetaan, kun hakuehto on piste"
      (is (true? (tielupa-q/valilla?
                   [2 1] nil (tielupa 1 1 3 1))))
      (is (true? (tielupa-q/valilla?
                   [2 1] nil (tielupa 2 1 3 1))))
      (is (true? (tielupa-q/valilla?
                   [2 30] nil (tielupa 2 10 3 1))))

      (is (false? (tielupa-q/valilla?
                    [2 30] nil (tielupa 2 40 3 1))))
      (is (false? (tielupa-q/valilla?
                    [1 30] nil (tielupa 2 40 3 1)))))

    (testing "Päällekkäisyys tunnistetaan, kun tieluvan sijainti on piste"
      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 2 1 nil nil)))
          "Tielupa on hakuehtojen välissä")
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 2 1 nil nil)))
          "Tielupa on hakuehtojen välissä")

      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 2 1 nil nil))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 2 1 nil nil))))

      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 1 1 nil nil))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 1 1 nil nil))))

      (is (true? (tielupa-q/valilla?
                   [1 1] [1 100] (tielupa 1 10 nil nil))))

      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 4 1 nil nil))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 4 1 nil nil))))
      (is (true? (tielupa-q/valilla?
                   [1 1] [4 1] (tielupa 2 1 nil nil))))
      (is (true? (tielupa-q/valilla?
                   [4 1] [1 1] (tielupa 2 1 nil nil))))

      (is (false? (tielupa-q/valilla?
                    [1 1] [4 1] (tielupa 0 1 nil nil))))
      (is (false? (tielupa-q/valilla?
                    [4 1] [1 1] (tielupa 0 1 nil nil)))))

    (testing "Päällekkäisyys tunnistetaan, kun molemmat ovat pisteitä"
      (is (false? (tielupa-q/valilla?
                    [2 1] nil (tielupa 1 1 nil nil))))
      (is (true? (tielupa-q/valilla?
                   [2 1] nil (tielupa 2 1 nil nil)))))))

(deftest suodata-tieosoittella
  (is (= (tielupa-q/suodata-tieosoitteella
           [{::tielupa/sijainnit [{::tielupa/tie 20
                                   ::tielupa/aosa 20
                                   ::tielupa/aet 20}
                                  {::tielupa/tie 20
                                   ::tielupa/aosa 20
                                   ::tielupa/aet 20}]}
            {::tielupa/sijainnit [{::tielupa/tie 20
                                   ::tielupa/aosa 20
                                   ::tielupa/aet 20}
                                  {::tielupa/tie 1
                                   ::tielupa/aosa 1
                                   ::tielupa/aet 1}]}
            {::tielupa/sijainnit [{::tielupa/tie 1
                                   ::tielupa/aosa 1
                                   ::tielupa/aet 1}
                                  {::tielupa/tie 1
                                   ::tielupa/aosa 1
                                   ::tielupa/aet 1}]}]
           {::tielupa/tie 1
            ::tielupa/aosa 1
            ::tielupa/aet 1})
         [{::tielupa/sijainnit [{::tielupa/tie 20
                                 ::tielupa/aosa 20
                                 ::tielupa/aet 20}
                                {::tielupa/tie 1
                                 ::tielupa/aosa 1
                                 ::tielupa/aet 1}]}
          {::tielupa/sijainnit [{::tielupa/tie 1
                                 ::tielupa/aosa 1
                                 ::tielupa/aet 1}
                                {::tielupa/tie 1
                                 ::tielupa/aosa 1
                                 ::tielupa/aet 1}]}])
      "Tielupa, jonka yksikään sijainti osuu halutulle välille, pitää palauttaa"))


(deftest paattele-alueurakka
  ;; TODO: Kirjoita testi, vaatii kyselyn alueurakan ja urakan tarkistamiseksi
  ;; Sanomassa tr-osoite
  ;; Sanomassa ei tr-osoitetta
  ;;(assoc testitielupa ::tielupa/sijainnit [])
  )

(deftest filteroi-tieluvat-urakan-perusteella
  (let [urakka-id (hae-oulun-aktiivinen-testi-id)
        kaikki-tieluvat (tielupa-q/hae-tieluvat-hakunakymaan ds +kayttaja-jvh+ {})
        tieluvat-filteroityna-urakalla (tielupa-q/hae-tieluvat-hakunakymaan ds +kayttaja-jvh+ {:urakka-id urakka-id})
        urakat (mapcat ::tielupa/urakat tieluvat-filteroityna-urakalla)]
    (is (and (every? #(= urakka-id %)
                     urakat)
             (not (empty? urakat)))
        "Tielupien filtteröinti urakalla ei toimi")
    (is (> (count kaikki-tieluvat) (count tieluvat-filteroityna-urakalla)))))

(deftest toimiiko-katselmus-url-filtterointi
  (let [oulun-aktiivinen-testi-urakka-id (hae-oulun-aktiivinen-testi-id)
        tieluvat-jvhna (tielupa-q/hae-tieluvat-hakunakymaan ds +kayttaja-jvh+ {})
        tieluvat-aktiivinen-oulu-testi-urakka-kayttajana (tielupa-q/hae-tieluvat-hakunakymaan ds +kayttaja-oulu-aktiivinen-urakka-vastuuhenkilo+ {})]
    (is (every? #(not (nil? (::tielupa/katselmus-url %)))
                tieluvat-jvhna))
    (is (every? #(or (and (not (nil? (::tielupa/katselmus-url %)))
                          (= oulun-aktiivinen-testi-urakka-id (first (::tielupa/urakat %))))
                     (and (nil? (::tielupa/katselmus-url %))
                          (not= oulun-aktiivinen-testi-urakka-id (first (::tielupa/urakat %)))))
                tieluvat-aktiivinen-oulu-testi-urakka-kayttajana))))