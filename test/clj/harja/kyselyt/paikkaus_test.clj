(ns harja.kyselyt.paikkaus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [taoensso.timbre :as log]))

(use-fixtures :each urakkatieto-fixture)

(def testipaikkauksen-ulkoinen-id 666123)
(def testikohteen-ulkoinen-id 666567)
(def testipaikkaustoteuman-ulkoinen-id 666987)
(def destian-kayttaja-id (ffirst (q "select id from kayttaja where kayttajanimi = 'destia';")))
(def oikean-urakan-id (hae-oulun-alueurakan-2014-2019-id))

(def testipaikkaus
  {::paikkaus/alkuaika #inst"2018-02-06T10:47:24.183975000-00:00"
   ::paikkaus/tyomenetelma "massapintaus"
   ::paikkaus/paikkauskohde-id 1
   ::paikkaus/raekoko 1
   ::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id
   ::paikkaus/leveys 1.3M
   ::paikkaus/urakka-id oikean-urakan-id
   :harja.domain.muokkaustiedot/luoja-id destian-kayttaja-id
   ::paikkaus/tierekisteriosoite {:harja.domain.tierekisteri/aet 1
                                  :harja.domain.tierekisteri/let 100
                                  :harja.domain.tierekisteri/tie 20
                                  :harja.domain.tierekisteri/aosa 1
                                  :harja.domain.tierekisteri/losa 1}
   ::paikkaus/massatyyppi "asfalttibetoni"
   ::paikkaus/kuulamylly "2"
   ::paikkaus/paikkauskohde {::paikkaus/ulkoinen-id testikohteen-ulkoinen-id
                                         ::paikkaus/nimi "Testikohde"}
   ::paikkaus/loppuaika #inst"2018-02-06T10:47:24.183975000-00:00"
   ::paikkaus/massamenekki 2
   ::paikkaus/materiaalit [{::paikkaus/esiintyma "Testikivi"
                                        ::paikkaus/kuulamylly-arvo "1"
                                        ::paikkaus/muotoarvo "Muotoarvo"
                                        ::paikkaus/lisa-aineet "Lisäaineet"
                                        ::paikkaus/pitoisuus 3.2M
                                        ::paikkaus/sideainetyyppi "70/100"}]
   ::paikkaus/tienkohdat [{::paikkaus/ajourat [1 2]
                                       ::paikkaus/ajorata 1
                                       ::paikkaus/ajouravalit [1]
                                       ::paikkaus/reunat [0]}]})

(def testipaikkaustoteuma
  {::paikkaus/selite "Testi"
   ::paikkaus/urakka-id 4
   ::paikkaus/hinta 3500M
   ::paikkaus/paikkauskohde {::paikkaus/ulkoinen-id testikohteen-ulkoinen-id
                                         ::paikkaus/nimi "Testikohde"}
   ::paikkaus/ulkoinen-id testipaikkaustoteuman-ulkoinen-id
   ::paikkaus/tyyppi "kokonaishintainen"
   ::paikkaus/kirjattu #inst"2018-02-22T08:00:15.937759000-00:00"})

(deftest hae-paikkaustoimenpiteet
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        ulkoinen-id 6661
        vastaus (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id ulkoinen-id})]
    (is (every? #(= ulkoinen-id (::paikkaus/ulkoinen-id %)) vastaus) "Jokainen löytynyt tietue vastaa hakuehtoa")))

(deftest onko-olemassa-ulkoisella-idlla
  (let [db (tietokanta/luo-tietokanta testitietokanta)]
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id nil destian-kayttaja-id)) "Nil ei palauta tietoja paikkauskohteesta.")
    (is (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id 666 destian-kayttaja-id)) "Oikeilla tiedoilla löytyy paikkauskohde.")
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db 1234 666 destian-kayttaja-id)) "Väärällä urakka-id:llä ei löydy paikkauskohdetta.")
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id 2345 destian-kayttaja-id)) "Väärällä ulkoisella tunnisteella (int) ei paikkauskohdetta.")
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id "foo" destian-kayttaja-id)) "Väärällä ulkoisella tunnisteella (string) ei löydy paikkauskohdetta.")

    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id nil destian-kayttaja-id)) "Nil ei palauta tietoja paikkauksesta.")
    (is (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id 6661 destian-kayttaja-id)) "Oikeilla tiedoilla löytyy paikkaus.")
    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db 1234 6661 destian-kayttaja-id)) "Väärällä urakka-id:llä ei löydy paikkausta.")
    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id 2345 destian-kayttaja-id)) "Väärällä ulkoisella tunnisteella (int) ei paikkausta.")
    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id "foo" destian-kayttaja-id)) "Väärällä ulkoisella tunnisteella (string) ei löydy paikkausta.")

    (is (false? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db oikean-urakan-id nil destian-kayttaja-id)) "Nil ei palauta tietoja paikkaustoteumasta.")
    (is (true? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db oikean-urakan-id 133 destian-kayttaja-id)) "Oikeilla tiedoilla löytyy paikkaustoteuma.")
    (is (false? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db 1234 133 destian-kayttaja-id)) "Väärällä urakka-id:llä ei löydy paikkaustoteumaa.")
    (is (false? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db oikean-urakan-id 2345 destian-kayttaja-id)) "Väärällä ulkoisella tunnisteella (int) ei paikkaustoteumaa.")
    (is (false? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db oikean-urakan-id "foo" destian-kayttaja-id)) "Väärällä ulkoisella tunnisteella (string) ei löydy paikkaustoteumaa.")))

(defn hae-testipaikkaus [db]
  (first (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id})))

(defn hae-testipaikkauksen-materiaalit [db]
  (first (paikkaus-q/hae-paikkaukset-materiaalit db {::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id})))

(defn hae-testipaikkauksen-tienkohta [db]
  (first (paikkaus-q/hae-paikkaukset-tienkohta db {::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id})))

(defn hae-testipaikkaustoteuma [db]
  (first (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id testipaikkaustoteuman-ulkoinen-id})))

(defn hae-paikkausten-maara []
  (ffirst (q "select count (id) from paikkaus;")))

(defn hae-kohteiden-maara []
  (ffirst (q "select count (id) from paikkauskohde;")))

(defn hae-paikkaustoteumien-maara []
  (ffirst (q "select count (id) from paikkaustoteuma;")))

(deftest luo-uusi-paikkaus-ja-paikkauskohde
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id testipaikkaus)
    (is (= (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id testipaikkauksen-ulkoinen-id destian-kayttaja-id)))
        "Toteuma löytyy ulkoisella id:lla")
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara))
        "Toteumien määrä on noussut yhdellä")
    (is (= (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id testikohteen-ulkoinen-id destian-kayttaja-id)))
        "Kohde löytyy ulkoisella id:lla")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara))
        "Kohteiden määrä on noussut yhdellä")

    (let [toteuma (hae-testipaikkaus db)
          materiaalit (::paikkaus/materiaalit (hae-testipaikkauksen-materiaalit db))
          tienkohdat (::paikkaus/tienkohdat (hae-testipaikkauksen-tienkohta db))]
      (is (= [{::paikkaus/esiintyma "Testikivi"
               ::paikkaus/kuulamylly-arvo "1"
               ::paikkaus/muotoarvo "Muotoarvo"
               ::paikkaus/lisa-aineet "Lisäaineet"
               ::paikkaus/pitoisuus 3.2M
               ::paikkaus/sideainetyyppi "70/100"}]
             [(dissoc (first materiaalit)
                      ::paikkaus/materiaali-id)])
          "Oletetut materiaalit löytyvät")
      (is (= [{::paikkaus/ajourat [1 2]
               ::paikkaus/ajorata 1
               ::paikkaus/ajouravalit [1]
               ::paikkaus/reunat [0]}]
             [(dissoc (first tienkohdat)
                      ::paikkaus/tienkohta-id)])
          "Oletetut tienkohdat löytyvät"))))

(deftest paivita-paikkaus-tai-paikkauskohde
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id testipaikkaus)
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uusi paikkaus luotiin")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uusi kohde luotiin")

    ;; Tutkitaan tallennuksen lopputulos
    (is (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id testipaikkauksen-ulkoinen-id destian-kayttaja-id)))
    (is (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id testikohteen-ulkoinen-id destian-kayttaja-id)))

    ;; ulkoisella id:lla paivittaminen
    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id (assoc testipaikkaus ::paikkaus/massatyyppi "kivimastiks"))
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uutta paikkausta ei luotu")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uutta kohdetta ei luotu")
    (is (= "kivimastiks" (::paikkaus/massatyyppi (hae-testipaikkaus db))) "Massatyyppi on päivitetty oikein")

    ;; harjan id:lla paivittaminen
    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id (assoc testipaikkaus
                                                           ::paikkaus/id (::paikkaus/id (hae-testipaikkaus db))
                                                           ::paikkaus/massatyyppi "pehmeät ab / bitumi"))
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uutta paikkausta ei luotu")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uutta kohdetta ei luotu")
    (is (= "pehmeät ab / bitumi" (::paikkaus/massatyyppi (hae-testipaikkaus db))) "Massatyyppi on päivitetty oikein")))

(deftest kohteiden-paivittaminen
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)
        uuden-kohteen-ulkoinen-id 12345]
    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id testipaikkaus)
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uusi kohde luotiin")

    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id (assoc testipaikkaus ::paikkaus/paikkauskohde
                                                                              {::paikkaus/ulkoinen-id uuden-kohteen-ulkoinen-id
                                                                               ::paikkaus/nimi "Testikohde"}))
    (is (= (+ kohteiden-maara-luonnin-jalkeen 1) (hae-kohteiden-maara)) "Uusi kohde luotiin")
    (is (= 1 (count (paikkaus-q/hae-paikkauskohteet db {::paikkaus/ulkoinen-id uuden-kohteen-ulkoinen-id}))))))

(deftest luo-uusi-paikkaustoteuma
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        toteumien-maara-luonnin-jalkeen (+ (hae-paikkaustoteumien-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaustoteuma db oikean-urakan-id destian-kayttaja-id testipaikkaustoteuma)
    (is (= (true? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla?
                    db
                    oikean-urakan-id
                    testipaikkauksen-ulkoinen-id
                    destian-kayttaja-id)))
        "Toteuma löytyy ulkoisella id:lla")
    (is (= toteumien-maara-luonnin-jalkeen (hae-paikkaustoteumien-maara))
        "Toteumien määrä on noussut yhdellä")
    (is (= (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id testikohteen-ulkoinen-id destian-kayttaja-id)))
        "Kohde löytyy ulkoisella id:lla")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara))
        "Kohteiden määrä on noussut yhdellä")

    (let [toteuma (hae-testipaikkaustoteuma db)]
      (is (= (dissoc toteuma
                     ::paikkaus/kirjattu
                     ::paikkaus/id
                     ::paikkaus/paikkauskohde-id)
             {::paikkaus/selite "Testi"
              ::paikkaus/urakka-id 4
              ::paikkaus/hinta 3500M
              ::paikkaus/ulkoinen-id 666987
              ::paikkaus/tyyppi "kokonaishintainen"})))))

(deftest poista-paikkaustoteuma
  (let [db (tietokanta/luo-tietokanta testitietokanta)]
    (paikkaus-q/tallenna-paikkaustoteuma db oikean-urakan-id destian-kayttaja-id testipaikkaustoteuma)
    (is (= 1 (first(first(q (str "select count (id) from paikkaustoteuma where poistettu is not true and \"ulkoinen-id\" = " testipaikkaustoteuman-ulkoinen-id " ;"))))) "Paikkaustoteuma löytyy tallennuksen jälkeen.")
    (paikkaus-q/paivita-paikkaustoteumat-poistetuksi db destian-kayttaja-id oikean-urakan-id [testipaikkaustoteuman-ulkoinen-id])
    (is (= 0 (first(first(q (str "select count (id) from paikkaustoteuma where poistettu is not true and \"ulkoinen-id\" = " testipaikkaustoteuman-ulkoinen-id " ;" ))))) "Paikkaustoteuma ei ole voimassa poiston jälkeen.")
    (is (= 1 (first(first(q (str "select count (id) from paikkaustoteuma where poistettu is true and \"ulkoinen-id\" = " testipaikkaustoteuman-ulkoinen-id " ;" ))))) "Paikkaustoteuma on merkitty poistetuksi.")))
