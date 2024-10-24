(ns harja.kyselyt.paikkaus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.domain.paikkaus :as paikkaus]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (urakkatieto-alustus!)
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)))))
  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!)
  (urakkatieto-lopetus!))

(use-fixtures :each jarjestelma-fixture)

(def testipaikkauksen-ulkoinen-id 666123)
(def testikohteen-ulkoinen-id 666567)
(def testipaikkaustoteuman-ulkoinen-id 666987)
(def destian-kayttaja-id (ffirst (q "select id from kayttaja where kayttajanimi = 'destia';")))
(def yit-rakennus-kayttaja-id (ffirst (q "select id from kayttaja where kayttajanimi = 'yit-rakennus';")))
(def oikean-urakan-id (hae-oulun-alueurakan-2014-2019-id))

(def testipaikkaus
  {::paikkaus/alkuaika #inst"2018-02-06T10:47:24.183975000-00:00"
   ::paikkaus/tyomenetelma "AB-paikkaus levittäjällä"
   ::paikkaus/paikkauskohde-id 1
   ::paikkaus/raekoko 1
   ::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id
   ::paikkaus/leveys 1.3M
   ::paikkaus/urakka-id oikean-urakan-id
   :harja.domain.muokkaustiedot/luoja-id destian-kayttaja-id
   ::paikkaus/tierekisteriosoite {:harja.domain.tierekisteri/aet 3290
                                  :harja.domain.tierekisteri/let 10
                                  :harja.domain.tierekisteri/tie 12
                                  :harja.domain.tierekisteri/aosa 101
                                  :harja.domain.tierekisteri/losa 102}
   ::paikkaus/massatyyppi "AB, Asfalttibetoni"
   ::paikkaus/kuulamylly "2"
   ::paikkaus/paikkauskohde {::paikkaus/ulkoinen-id testikohteen-ulkoinen-id
                                         ::paikkaus/nimi "Testikohde"}
   ::paikkaus/loppuaika #inst"2018-02-06T10:47:24.183975000-00:00"
   ::paikkaus/massamenekki 2
   ::paikkaus/massamaara 20
   ::paikkaus/materiaalit [{::paikkaus/esiintyma "Testikivi"
                                        ::paikkaus/kuulamylly-arvo "1"
                                        ::paikkaus/muotoarvo "Muotoarvo"
                                        ::paikkaus/lisa-aineet "Lisäaineet"
                                        ::paikkaus/pitoisuus 3.2M
                                        ::paikkaus/sideainetyyppi "70/100"}]
   ::paikkaus/tienkohdat [{::paikkaus/ajourat [1 2]
                                       ::paikkaus/ajorata 1
                                       ::paikkaus/ajouravalit [1]
                                       ::paikkaus/reunat [1]}]})

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
  (let [db (:db jarjestelma)
        ulkoinen-id 6661
        vastaus (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id ulkoinen-id})]
    (is (every? #(= ulkoinen-id (::paikkaus/ulkoinen-id %)) vastaus) "Jokainen löytynyt tietue vastaa hakuehtoa")))

(deftest onko-olemassa-ulkoisella-idlla
  (let [db (:db jarjestelma)]
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id nil)) "Nil ei palauta tietoja paikkauskohteesta.")
    (is (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id 666)) "Oikeilla tiedoilla löytyy paikkauskohde.")
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db 1234 666)) "Väärällä urakka-id:llä ei löydy paikkauskohdetta.")
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id 2345)) "Väärällä ulkoisella tunnisteella (int) ei paikkauskohdetta.")
    (is (false? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id "foo")) "Väärällä ulkoisella tunnisteella (string) ei löydy paikkauskohdetta.")

    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id nil)) "Nil ei palauta tietoja paikkauksesta.")
    (is (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id 6661)) "Oikeilla tiedoilla löytyy paikkaus.")
    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db 1234 6661)) "Väärällä urakka-id:llä ei löydy paikkausta.")
    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id 2345)) "Väärällä ulkoisella tunnisteella (int) ei paikkausta.")
    (is (false? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id "foo")) "Väärällä ulkoisella tunnisteella (string) ei löydy paikkausta.")

    (is (false? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db oikean-urakan-id nil)) "Nil ei palauta tietoja paikkaustoteumasta.")
    (is (true? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db oikean-urakan-id 133)) "Oikeilla tiedoilla löytyy paikkaustoteuma.")
    (is (false? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db 1234 133)) "Väärällä urakka-id:llä ei löydy paikkaustoteumaa.")
    (is (false? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db oikean-urakan-id 2345)) "Väärällä ulkoisella tunnisteella (int) ei paikkaustoteumaa.")
    (is (false? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla? db oikean-urakan-id "foo")) "Väärällä ulkoisella tunnisteella (string) ei löydy paikkaustoteumaa.")))

(defn hae-testipaikkaus [db id]
  (first (paikkaus-q/hae-paikkaukset db {::paikkaus/ulkoinen-id id})))

(defn hae-testipaikkauksen-materiaalit [db id]
  (first (paikkaus-q/hae-paikkaukset-materiaalit db {::paikkaus/ulkoinen-id id})))

(defn hae-testipaikkauksen-tienkohta [db id]
  (first (paikkaus-q/hae-paikkaukset-tienkohta db {::paikkaus/ulkoinen-id id})))

(defn hae-testipaikkaustoteuma [db id]
  (first (paikkaus-q/hae-paikkaustoteumat db {::paikkaus/ulkoinen-id id})))

(defn hae-paikkausten-maara []
  (ffirst (q "select count (id) from paikkaus;")))

(defn hae-kohteiden-maara []
  (ffirst (q "select count (id) from paikkauskohde;")))

(defn hae-paikkaustoteumien-maara []
  (ffirst (q "select count (id) from paikkaustoteuma;")))

(deftest luo-uusi-paikkaus-ja-paikkauskohde
  (let [db (:db jarjestelma)
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id testipaikkaus)
    (is (= (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id testipaikkauksen-ulkoinen-id)))
        "Toteuma löytyy ulkoisella id:lla")
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara))
        "Toteumien määrä on noussut yhdellä")
    (is (= (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id testikohteen-ulkoinen-id)))
        "Kohde löytyy ulkoisella id:lla")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara))
        "Kohteiden määrä on noussut yhdellä")

    (let [toteuma (hae-testipaikkaus db testipaikkauksen-ulkoinen-id)
          materiaalit (::paikkaus/materiaalit (hae-testipaikkauksen-materiaalit db testipaikkauksen-ulkoinen-id))
          tienkohdat (::paikkaus/tienkohdat (hae-testipaikkauksen-tienkohta db testipaikkauksen-ulkoinen-id))]
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
               ::paikkaus/reunat [1]}]
             [(dissoc (first tienkohdat)
                      ::paikkaus/tienkohta-id)])
          "Oletetut tienkohdat löytyvät"))))

(deftest paivita-paikkaus-tai-paikkauskohde
  (let [db (:db jarjestelma)
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id testipaikkaus)
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uusi paikkaus luotiin")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uusi kohde luotiin")

    ;; Tutkitaan tallennuksen lopputulos
    (is (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id testipaikkauksen-ulkoinen-id)))
    (is (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id testikohteen-ulkoinen-id)))

    ;; ulkoisella id:lla paivittaminen
    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id (assoc testipaikkaus ::paikkaus/massatyyppi "SMA, Kivimastiksiasfaltti"))
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uutta paikkausta ei luotu")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uutta kohdetta ei luotu")
    (is (= "SMA, Kivimastiksiasfaltti" (::paikkaus/massatyyppi (hae-testipaikkaus db testipaikkauksen-ulkoinen-id))) "Massatyyppi on päivitetty oikein")

    ;; harjan id:lla paivittaminen
    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id (assoc testipaikkaus
                                                           ::paikkaus/id (::paikkaus/id (hae-testipaikkaus db testipaikkauksen-ulkoinen-id))
                                                           ::paikkaus/massatyyppi "PAB-B, Pehmeät asfalttibetonit"))
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara)) "Uutta paikkausta ei luotu")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara)) "Uutta kohdetta ei luotu")
    (is (= "PAB-B, Pehmeät asfalttibetonit" (::paikkaus/massatyyppi (hae-testipaikkaus db testipaikkauksen-ulkoinen-id))) "Massatyyppi on päivitetty oikein")))

(deftest kohteiden-paivittaminen
  (let [db (:db jarjestelma)
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
  (let [db (:db jarjestelma)
        toteumien-maara-luonnin-jalkeen (+ (hae-paikkaustoteumien-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)]

    (paikkaus-q/tallenna-paikkaustoteuma db oikean-urakan-id destian-kayttaja-id testipaikkaustoteuma)
    (is (= (true? (paikkaus-q/onko-paikkaustoteuma-olemassa-ulkoisella-idlla?
                    db
                    oikean-urakan-id
                    testipaikkauksen-ulkoinen-id)))
        "Toteuma löytyy ulkoisella id:lla")
    (is (= toteumien-maara-luonnin-jalkeen (hae-paikkaustoteumien-maara))
        "Toteumien määrä on noussut yhdellä")
    (is (= (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id testikohteen-ulkoinen-id)))
        "Kohde löytyy ulkoisella id:lla")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara))
        "Kohteiden määrä on noussut yhdellä")

    (let [toteuma (hae-testipaikkaustoteuma db testipaikkaustoteuman-ulkoinen-id)]
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
  (let [db (:db jarjestelma)]
    (paikkaus-q/tallenna-paikkaustoteuma db oikean-urakan-id destian-kayttaja-id testipaikkaustoteuma)
    (is (= 1 (first(first(q (str "select count (id) from paikkaustoteuma where poistettu is not true and \"ulkoinen-id\" = " testipaikkaustoteuman-ulkoinen-id " ;"))))) "Paikkaustoteuma löytyy tallennuksen jälkeen.")
    (paikkaus-q/paivita-paikkaustoteumat-poistetuksi db destian-kayttaja-id oikean-urakan-id [testipaikkaustoteuman-ulkoinen-id])
    (is (= 0 (first(first(q (str "select count (id) from paikkaustoteuma where poistettu is not true and \"ulkoinen-id\" = " testipaikkaustoteuman-ulkoinen-id " ;" ))))) "Paikkaustoteuma ei ole voimassa poiston jälkeen.")
    (is (= 1 (first(first(q (str "select count (id) from paikkaustoteuma where poistettu is true and \"ulkoinen-id\" = " testipaikkaustoteuman-ulkoinen-id " ;" ))))) "Paikkaustoteuma on merkitty poistetuksi.")))

(deftest luo-uusi-paikkaus-ja-paikkauskohde-massamaaralla
  (let [db (:db jarjestelma)
        paikkausten-maara-luonnin-jalkeen (+ (hae-paikkausten-maara) 1)
        kohteiden-maara-luonnin-jalkeen (+ (hae-kohteiden-maara) 1)
        testipaikkauksen-ulkoinen-id (inc testipaikkauksen-ulkoinen-id)
        testikohteen-ulkoinen-id (inc testikohteen-ulkoinen-id)
        testipaikkaus (-> testipaikkaus
                        (dissoc ::paikkaus/massamenekki)
                        (assoc ::paikkaus/massamaara 260)
                        (assoc ::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id)
                        (assoc-in [::paikkaus/paikkauskohde ::paikkaus/ulkoinen-id] testikohteen-ulkoinen-id))]

    (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id testipaikkaus)
    (is (= (true? (paikkaus-q/onko-paikkaus-olemassa-ulkoisella-idlla? db oikean-urakan-id testipaikkauksen-ulkoinen-id)))
      "Toteuma löytyy ulkoisella id:lla")
    (is (= paikkausten-maara-luonnin-jalkeen (hae-paikkausten-maara))
      "Toteumien määrä on noussut yhdellä")
    (is (= (true? (paikkaus-q/onko-kohde-olemassa-ulkoisella-idlla? db oikean-urakan-id testikohteen-ulkoinen-id)))
      "Kohde löytyy ulkoisella id:lla")
    (is (= kohteiden-maara-luonnin-jalkeen (hae-kohteiden-maara))
      "Kohteiden määrä on noussut yhdellä")


    (let [toteuma (hae-testipaikkaus db testipaikkauksen-ulkoinen-id)
          materiaalit (::paikkaus/materiaalit (hae-testipaikkauksen-materiaalit db testipaikkauksen-ulkoinen-id))
          tienkohdat (::paikkaus/tienkohdat (hae-testipaikkauksen-tienkohta db testipaikkauksen-ulkoinen-id))]

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
               ::paikkaus/reunat [1]}]
            [(dissoc (first tienkohdat)
               ::paikkaus/tienkohta-id)])
        "Oletetut tienkohdat löytyvät"))))

(deftest luo-uusi-paikkaus-epavalidilla-massamaara-arvolla
  (let [db (:db jarjestelma)
        testipaikkauksen-ulkoinen-id (inc testipaikkauksen-ulkoinen-id)
        testikohteen-ulkoinen-id (inc testikohteen-ulkoinen-id)
        testipaikkaus (-> testipaikkaus
                        (dissoc ::paikkaus/massamenekki)
                        (assoc ::paikkaus/massamaara "a")
                        (assoc ::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id)
                        (assoc-in [::paikkaus/paikkauskohde ::paikkaus/ulkoinen-id] testikohteen-ulkoinen-id))
        vastaus (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id testipaikkaus)]
(is (= "Paikkaus ei ole validi. Tarkista tiedot." vastaus))))

(deftest luo-uusi-paikkaus-ja-paikkauskohde-vaaralla-reunat-arvolla
  (let [db (:db jarjestelma)
        testipaikkauksen-ulkoinen-id (inc testipaikkauksen-ulkoinen-id)
        testikohteen-ulkoinen-id (inc testikohteen-ulkoinen-id)
        testipaikkaus (-> testipaikkaus
                        (dissoc ::paikkaus/massamenekki)
                        (assoc ::paikkaus/massamaara 260)
                        (assoc ::paikkaus/ulkoinen-id testipaikkauksen-ulkoinen-id)
                        (assoc-in [::paikkaus/paikkauskohde ::paikkaus/ulkoinen-id] testikohteen-ulkoinen-id)
                        ;; Virheellinen reunat arvo - pitäisi olla 1 tai 2
                        (assoc-in [::paikkaus/tienkohdat 0 ::paikkaus/reunat] [0]))
        vastaus (paikkaus-q/tallenna-paikkaus db oikean-urakan-id destian-kayttaja-id testipaikkaus)]

    (is (= "Paikkaus ei ole validi. Tarkista tiedot." vastaus))))
