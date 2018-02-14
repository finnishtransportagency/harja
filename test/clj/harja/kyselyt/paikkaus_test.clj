(ns harja.kyselyt.paikkaus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.testi :as testi]))

(use-fixtures :each urakkatieto-fixture)

(def testipaikkauksen-ulkoinen-id 666123)
(def testikohteen-ulkoinen-id 666567)

(def testipaikkaus
  {:harja.domain.paikkaus/alkuaika #inst"2018-02-06T10:47:24.183975000-00:00"
   :harja.domain.paikkaus/tyomenetelma "massapintaus"
   :harja.domain.paikkaus/paikkauskohde-id 1
   :harja.domain.paikkaus/raekoko 1
   :harja.domain.paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id
   :harja.domain.paikkaus/leveys 1.3M
   :harja.domain.paikkaus/urakka-id @testi/oulun-alueurakan-2014-2019-id
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
   :harja.domain.paikkaus/materiaalit [{:harja.domain.paikkaus/esiintyma "Testikivi",
                                        :harja.domain.paikkaus/kuulamylly-arvo "1",
                                        :harja.domain.paikkaus/muotoarvo "Muotoarvo",
                                        :harja.domain.paikkaus/lisa-aineet "Lisäaineet",
                                        :harja.domain.paikkaus/pitoisuus 3.2M,
                                        :harja.domain.paikkaus/sideainetyyppi "Sideaine"}]
   :harja.domain.paikkaus/tienkohdat [{:harja.domain.paikkaus/ajourat [1 2]
                                       :harja.domain.paikkaus/ajorata 1
                                       :harja.domain.paikkaus/ajouravalit [1]
                                       :harja.domain.paikkaus/reunat [0]}]})

(deftest hae-paikkaustoimenpiteet
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        ulkoinen-id 6661
        vastaus (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id ulkoinen-id})]
    (is (every? #(= ulkoinen-id (::paikkaus/ulkoinen-id %)) vastaus) "Jokainen löytynyt tietue vastaa hakuehtoa")))

(deftest onko-olemassa-ulkoisella-idlla
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        destian-kayttaja-id (ffirst (q "select id from kayttaja where kayttajanimi = 'destia';"))]
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db nil)))
    (is (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db 666)))
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db 2345)))
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db "foo")))

    (is (false? (paikkaus-q/onko-toteuma-olemassa-ulkoisella-idlla? db nil destian-kayttaja-id)))
    (is (true? (paikkaus-q/onko-toteuma-olemassa-ulkoisella-idlla? db 6661 destian-kayttaja-id)))
    (is (false? (paikkaus-q/onko-toteuma-olemassa-ulkoisella-idlla? db 2345 destian-kayttaja-id)))
    (is (false? (paikkaus-q/onko-toteuma-olemassa-ulkoisella-idlla? db "foo" destian-kayttaja-id)))))

(defn hae-testitoteuma [db]
  (first (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id})))

(defn hae-paikkausten-maara []
  (ffirst (q "select count (id) from paikkaustoteuma;")))

(defn hae-kohteiden-maara []
  (ffirst (q "select count (id) from paikkauskohde;")))

(deftest luo-uusi-paikkaus
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        destian-kayttaja-id (ffirst (q "select id from kayttaja where kayttajanimi = 'destia';"))
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)]

    (paikkaus-q/tallenna-paikkaustoteuma db destian-kayttaja-id testipaikkaus)
    (is (= (true? (paikkaus-q/onko-toteuma-olemassa-ulkoisella-idlla? db testipaikkauksen-ulkoinen-id destian-kayttaja-id)))
        "Toteuma löytyy ulkoisella id:lla")
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara))
        "Toteumien määrä on noussut yhdellä")
    (is (= (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db testikohteen-ulkoinen-id)))
        "Kohde löytyy ulkoisella id:lla")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara))
        "Kohteiden määrä on noussut yhdellä")

    (let [toteuma (hae-testitoteuma db)
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

(deftest paivita-paikkaustoteuma
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        destian-kayttaja-id (ffirst (q "select id from kayttaja where kayttajanimi = 'destia';"))
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaustoteuma db destian-kayttaja-id testipaikkaus)
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uusi paikkaus luotiin")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uusi kohde luotiin")

    ;; ulkoisella id:lla paivittaminen
    (paikkaus-q/tallenna-paikkaustoteuma db destian-kayttaja-id (assoc testipaikkaus ::paikkaus/massatyyppi "kivimastiks"))
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uutta paikkausta ei luotu")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uutta kohdetta ei luotu")
    (is (= "kivimastiks" (::paikkaus/massatyyppi (hae-testitoteuma db))) "Massatyyppi on päivitetty oikein")

    ;; harjan id:lla paivittaminen
    (paikkaus-q/tallenna-paikkaustoteuma db destian-kayttaja-id (assoc testipaikkaus
                                                                  ::paikkaus/id (::paikkaus/id (hae-testitoteuma db))
                                                                  ::paikkaus/massatyyppi "pehmeät ab / bitumi"))
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uutta paikkausta ei luotu")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uutta kohdetta ei luotu")
    (is (= "pehmeät ab / bitumi" (::paikkaus/massatyyppi (hae-testitoteuma db))) "Massatyyppi on päivitetty oikein")))

(deftest kohteiden-paivittaminen
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        destian-kayttaja-id (ffirst (q "select id from kayttaja where kayttajanimi = 'destia';"))
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)
        uuden-kohteen-ulkoinen-id 12345]
    (paikkaus-q/tallenna-paikkaustoteuma db destian-kayttaja-id testipaikkaus)
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uusi kohde luotiin")

    (paikkaus-q/tallenna-paikkaustoteuma db destian-kayttaja-id (assoc testipaikkaus ::paikkaus/paikkauskohde
                                                                                     {:harja.domain.paikkaus/ulkoinen-id uuden-kohteen-ulkoinen-id
                                                                                      :harja.domain.paikkaus/nimi "Testikohde"}))
    (is (= (+ kohteiden-maara-luonnin-jalkeen 1) (hae-kohteiden-maara)) "Uusi kohde luotiin")
    (is (= 1 (count (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id uuden-kohteen-ulkoinen-id}))))))
