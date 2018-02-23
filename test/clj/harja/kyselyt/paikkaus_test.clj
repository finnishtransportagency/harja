(ns harja.kyselyt.paikkaus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.kyselyt.paikkaus :as paikkaus-q]))

(use-fixtures :each urakkatieto-fixture)

(def testipaikkauksen-ulkoinen-id 666123)
(def testikohteen-ulkoinen-id 666567)
(def testipaikkaustoteuman-ulkoinen-id 666987)
(def destian-kayttaja-id (ffirst (q "select id from kayttaja where kayttajanimi = 'destia';")))

(def testipaikkaus
  {:harja.domain.paikkaus/alkuaika #inst"2018-02-06T10:47:24.183975000-00:00"
   :harja.domain.paikkaus/tyomenetelma "massapintaus"
   :harja.domain.paikkaus/paikkauskohde-id 1
   :harja.domain.paikkaus/raekoko 1
   :harja.domain.paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id
   :harja.domain.paikkaus/leveys 1.3M
   :harja.domain.paikkaus/urakka-id (hae-oulun-alueurakan-2014-2019-id)
   :harja.domain.muokkaustiedot/luoja-id destian-kayttaja-id
   :harja.domain.paikkaus/tierekisteriosoite {:harja.domain.tierekisteri/aet 1
                                              :harja.domain.tierekisteri/let 100
                                              :harja.domain.tierekisteri/tie 20
                                              :harja.domain.tierekisteri/aosa 1
                                              :harja.domain.tierekisteri/losa 1}
   :harja.domain.paikkaus/massatyyppi "asfalttibetoni"
   :harja.domain.paikkaus/kuulamylly "2"
   :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/ulkoinen-id testikohteen-ulkoinen-id
                                         :harja.domain.paikkaus/nimi "Testikohde"}
   :harja.domain.paikkaus/loppuaika #inst"2018-02-06T10:47:24.183975000-00:00"
   :harja.domain.paikkaus/massamenekki 2
   :harja.domain.paikkaus/materiaalit [{:harja.domain.paikkaus/esiintyma "Testikivi"
                                        :harja.domain.paikkaus/kuulamylly-arvo "1"
                                        :harja.domain.paikkaus/muotoarvo "Muotoarvo"
                                        :harja.domain.paikkaus/lisa-aineet "Lisäaineet"
                                        :harja.domain.paikkaus/pitoisuus 3.2M
                                        :harja.domain.paikkaus/sideainetyyppi "Sideaine"}]
   :harja.domain.paikkaus/tienkohdat [{:harja.domain.paikkaus/ajourat [1 2]
                                       :harja.domain.paikkaus/ajorata 1
                                       :harja.domain.paikkaus/ajouravalit [1]
                                       :harja.domain.paikkaus/reunat [0]}]})

(def testipaikkaustoteuma
  {:harja.domain.paikkaus/selite "Testi"
   :harja.domain.paikkaus/urakka-id 4
   :harja.domain.paikkaus/hinta 3500M
   :harja.domain.paikkaus/paikkauskohde {:harja.domain.paikkaus/ulkoinen-id testikohteen-ulkoinen-id
                                         :harja.domain.paikkaus/nimi "Testikohde"}
   :harja.domain.paikkaus/ulkoinen-id testipaikkaustoteuman-ulkoinen-id
   :harja.domain.paikkaus/tyyppi "kokonaishintainen"
   :harja.domain.paikkaus/kirjattu #inst"2018-02-22T08:00:15.937759000-00:00"})

(deftest hae-paikkaustoimenpiteet
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        ulkoinen-id 6661
        vastaus (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id ulkoinen-id})]
    (is (every? #(= ulkoinen-id (::paikkaus/ulkoinen-id %)) vastaus) "Jokainen löytynyt tietue vastaa hakuehtoa")))

(deftest onko-olemassa-ulkoisella-idlla
  (let [db (tietokanta/luo-tietokanta testitietokanta)]
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db nil)))
    (is (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db 666)))
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db 2345)))
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db "foo")))

    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db nil destian-kayttaja-id)))
    (is (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db 6661 destian-kayttaja-id)))
    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db 2345 destian-kayttaja-id)))
    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db "foo" destian-kayttaja-id)))))

(defn hae-testipaikkaus [db]
  (first (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id})))

(defn hae-testipaikkaustoteuma [db]
  (first (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id testipaikkaustoteuman-ulkoinen-id})))

(defn hae-paikkausten-maara []
  (ffirst (q "select count (id) from paikkaus;")))

(defn hae-kohteiden-maara []
  (ffirst (q "select count (id) from paikkauskohde;")))

(defn hae-paikkaustoteumien-maara []
  (ffirst (q "select count (id) from paikkaustoteuma;")))

(deftest luo-uusi-paikkaus
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaus db destian-kayttaja-id testipaikkaus)
    (is (= (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db testipaikkauksen-ulkoinen-id destian-kayttaja-id)))
        "Toteuma löytyy ulkoisella id:lla")
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara))
        "Toteumien määrä on noussut yhdellä")
    (is (= (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db testikohteen-ulkoinen-id)))
        "Kohde löytyy ulkoisella id:lla")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara))
        "Kohteiden määrä on noussut yhdellä")

    (let [toteuma (hae-testipaikkaus db)
          materiaalit (::paikkaus/materiaalit toteuma)
          tienkohdat (::paikkaus/tienkohdat toteuma)]
      (is (= [{:harja.domain.paikkaus/esiintyma "Testikivi"
               :harja.domain.paikkaus/kuulamylly-arvo "1"
               :harja.domain.paikkaus/muotoarvo "Muotoarvo"
               :harja.domain.paikkaus/lisa-aineet "Lisäaineet"
               :harja.domain.paikkaus/pitoisuus 3.2M
               :harja.domain.paikkaus/sideainetyyppi "Sideaine"}]
             materiaalit)
          "Oletetut materiaalit löytyvät")
      (is (= [{:harja.domain.paikkaus/ajourat [1 2]
               :harja.domain.paikkaus/ajorata 1
               :harja.domain.paikkaus/ajouravalit [1]
               :harja.domain.paikkaus/reunat [0]}]
             tienkohdat)
          "Oletetut tienkohdat löytyvät"))))

(deftest paivita-paikkaus
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaus db destian-kayttaja-id testipaikkaus)
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uusi paikkaus luotiin")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uusi kohde luotiin")

    ;; ulkoisella id:lla paivittaminen
    (paikkaus-q/tallenna-paikkaus db destian-kayttaja-id (assoc testipaikkaus ::paikkaus/massatyyppi "kivimastiks"))
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uutta paikkausta ei luotu")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uutta kohdetta ei luotu")
    (is (= "kivimastiks" (::paikkaus/massatyyppi (hae-testipaikkaus db))) "Massatyyppi on päivitetty oikein")

    ;; harjan id:lla paivittaminen
    (paikkaus-q/tallenna-paikkaus db destian-kayttaja-id (assoc testipaikkaus
                                                           ::paikkaus/id (::paikkaus/id (hae-testipaikkaus db))
                                                           ::paikkaus/massatyyppi "pehmeät ab / bitumi"))
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uutta paikkausta ei luotu")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uutta kohdetta ei luotu")
    (is (= "pehmeät ab / bitumi" (::paikkaus/massatyyppi (hae-testipaikkaus db))) "Massatyyppi on päivitetty oikein")))

(deftest kohteiden-paivittaminen
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)
        uuden-kohteen-ulkoinen-id 12345]
    (paikkaus-q/tallenna-paikkaus db destian-kayttaja-id testipaikkaus)
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uusi kohde luotiin")

    (paikkaus-q/tallenna-paikkaus db destian-kayttaja-id (assoc testipaikkaus ::paikkaus/paikkauskohde
                                                                              {:harja.domain.paikkaus/ulkoinen-id uuden-kohteen-ulkoinen-id
                                                                               :harja.domain.paikkaus/nimi "Testikohde"}))
    (is (= (+ kohteiden-maara-luonnin-jalkeen 1) (hae-kohteiden-maara)) "Uusi kohde luotiin")
    (is (= 1 (count (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id uuden-kohteen-ulkoinen-id}))))))

(deftest luo-uusi-paikkaustoteuma
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        toteumien-maara-luonnin-jalkeen (+ (hae-paikkaustoteumien-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaustoteuma db destian-kayttaja-id testipaikkaustoteuma)
    (is (= (true? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla?
                    db
                    testipaikkauksen-ulkoinen-id
                    destian-kayttaja-id)))
        "Toteuma löytyy ulkoisella id:lla")
    (is (= toteumien-maara-luonnin-jalkeen (hae-paikkaustoteumien-maara))
        "Toteumien määrä on noussut yhdellä")
    (is (= (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db testikohteen-ulkoinen-id)))
        "Kohde löytyy ulkoisella id:lla")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara))
        "Kohteiden määrä on noussut yhdellä")

    (let [toteuma (hae-testipaikkaustoteuma db)]
      (is (= (dissoc toteuma :harja.domain.paikkaus/kirjattu )
             {:harja.domain.paikkaus/selite "Testi"
              :harja.domain.paikkaus/urakka-id 4
              :harja.domain.paikkaus/hinta 3500M
              :harja.domain.paikkaus/paikkauskohde-id 2
              :harja.domain.paikkaus/id 3
              :harja.domain.paikkaus/ulkoinen-id 666987
              :harja.domain.paikkaus/tyyppi "kokonaishintainen"})))))

(deftest poista-paikkaustoteuma
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        hae-toteuma #(paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id testipaikkaustoteuman-ulkoinen-id})]
    (paikkaus-q/tallenna-paikkaustoteuma db destian-kayttaja-id testipaikkaustoteuma)
    (is (= 1 (count (hae-toteuma))) "Paikkaustoteuma löytyy tallennuksen jälkeen")
    (paikkaus-q/poista-paikkaustoteumat db destian-kayttaja-id testipaikkaustoteuman-ulkoinen-id)
    (is (= 0 (count (hae-toteuma))) "Paikkaustoteuma ei löydy poiston jälkeen")))
