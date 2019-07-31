(ns harja.palvelin.palvelut.tehtavamaarat_test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tehtavamaarat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :tehtavamaarat (component/using
                                         (->Tehtavamaarat)
                                         [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(def paivitettavat-olemassaolevat-tehtavat
  [{:tehtava-id 1430 :maara 111}
   {:tehtava-id 1414 :maara 222}
   {:tehtava-id 4579 :maara 666.6}
   {:tehtava-id 4610 :maara 444}])

(def uudet-tehtavat
  [{:tehtava-id 1428 :maara 555}
   {:tehtava-id 4561 :maara 666}
   {:tehtava-id 4570 :maara 7.77}
   {:tehtava-id 4583 :maara 88.8}
   {:tehtava-id 4590 :maara 999}
   {:tehtava-id 4617 :maara 666}])

(def uuden-hoitokauden-tehtavat
  [{:tehtava-id 4589 :maara 6.66}
   {:tehtava-id 1430 :maara 999}])

(def virheellinen-tehtava
  [{:tehtava-id 4589 :maara 6.66}
   {:tehtava-id 666 :maara 999}])

;; TODO: hae urkakkanumerot älä kovakoodaa, muuta käyttäjä urakanvalvojaksi


;; jos tehtävähierarkian tehtävien tiedoissa tapahtuu muutoksia, tämä testi feilaa ja täytyy päivittää
(deftest tehtavahierarkian-haku
  (let [hierarkia (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tehtavahierarkia +kayttaja-jvh+)]
    (is (= (count hierarkia) 104) "Hierarkiassa on 104 osaa.")
    (is (= (:tehtava (first (filter #(= 4579 (:tehtava-id %)) hierarkia))) "Rumpujen tarkastus") "Tehtävähierarkiassa palautuu tietoja.")))


; käyttää ennakkoon tallennettua testidataa
(deftest tallenna-tehtavamaarat-testi
  (let [tehtavamaarat-ja-hierarkia (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tehtavamaarat-hierarkiassa +kayttaja-jvh+ {:urakka-id             32
                                                                                               :hoitokauden-alkuvuosi 2020})
        tehtavamaarat (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                     :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-kannassa (ffirst (q (str "SELECT count(*)
                                                             FROM urakka_tehtavamaara
                                                            WHERE \"hoitokauden-alkuvuosi\" = 2020 AND urakka = " 32)))

        tehtavamaarat-ennen-paivitysta (kutsu-palvelua (:http-palvelin jarjestelma)
                                                       :tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                                      :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-paivita (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                                      :hoitokauden-alkuvuosi 2020
                                                                                      :tehtavamaarat         paivitettavat-olemassaolevat-tehtavat})
        tehtavamaarat-paivityksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                                         :hoitokauden-alkuvuosi 2020})
        tehtavahierarkia-paivityksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                             :tehtavamaarat-hierarkiassa +kayttaja-jvh+ {:urakka-id             32
                                                                                                         :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-lisaa (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                                    :hoitokauden-alkuvuosi 2020
                                                                                    :tehtavamaarat         uudet-tehtavat})
        tehtavamaarat-lisayksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                                       :hoitokauden-alkuvuosi 2020})
        hoitokausi-2022-lisaa (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                                      :hoitokauden-alkuvuosi 2022
                                                                                      :tehtavamaarat         uuden-hoitokauden-tehtavat})
        hoitokausi-2022 (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                       :hoitokauden-alkuvuosi 2022})
        hoitokausi-2020 (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :tehtavamaarat +kayttaja-jvh+ {:urakka-id             32
                                                                       :hoitokauden-alkuvuosi 2020})]

    ;; tehtävähierarkia
    (is (= (count tehtavamaarat-ja-hierarkia) 104) "Hierarkiassa on 104 osaa.")
    (is (= (:maara (first (filter #(and (= 4579 (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= 32 (:urakka %))) tehtavamaarat-ja-hierarkia))) 32.6M) "Hoitokauden tehtävämäärä palautuu oikein hierarkiassa.")

    ;; tehtävämäärä
    (is (= (count tehtavamaarat) tehtavamaarat-kannassa) "Palutuneiden rivien lukumäärä vastaa kantaan tallennettuja.")
    (is (= (:maara (first (filter #(and (= 4579 (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= 32 (:urakka %))) tehtavamaarat))) 32.6M) "Hoitokauden tehtävämäärä palautuu oikein.")

    ;; hoitokauden tietojen päivitys
    (is (= (count tehtavamaarat-ennen-paivitysta) (count tehtavamaarat-paivityksen-jalkeen)) "Rivejä ei lisätty, kun tietoja päivitettiin.")
    (is (= tehtavamaarat-paivita tehtavahierarkia-paivityksen-jalkeen) "Tallennusfunktio palauttaa vastauksena kannan tilan samanlaisena kuin erillinen hierarkianhakufunktio.")
    (is (= (:maara (first (filter #(and (= 4579 (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= 32 (:urakka %))) tehtavamaarat-paivita))) 666.6M) "Päivitys päivitti määrän.")

    ;; hoitokauden tietojen lisäys
    (is (= (count tehtavamaarat-lisayksen-jalkeen) 10) "Uudet rivit lisättiin, vanhat säilyivät.")
    (is (= (:maara (first (filter #(and (= 4561 (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= 32 (:urakka %))) tehtavamaarat-lisaa))) 666M) "Lisäys lisäsi määrän.")

    ;; uuden hoitokauden lisäys
    (is (= (count hoitokausi-2022-lisaa) 104) "Uuden hoitokauden hierarkiassa palautuu oikea määrä tehtäviä.")
    (is (= (count hoitokausi-2022) 2) "Uudet rivit lisättiin oikealle hoitokaudelle.")
    (is (= (:maara (first (filter #(and (= 4589 (:tehtava-id %))
                                        (= 2022 (:hoitokauden-alkuvuosi %))
                                        (= 32 (:urakka %))) hoitokausi-2022-lisaa))) 6.66M) "Uuden hoitokauden tiedot palautettiin hierarkiassa.")
    (is (= (:maara (first (filter #(= 1430 (:tehtava-id %)) hoitokausi-2022))) 999M) "Uuden hoitokauden tehtävässä on oikea määrä.")
    (is (= (:maara (first (filter #(= 1430 (:tehtava-id %)) hoitokausi-2020))) 111M) "Uuden hoitokauden lisäys ei päivittänyt vanhaa hoitokautta.")))



(deftest tallenna-tehtavamaarat-virhekasittely-testi
  (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tehtavamaarat
                                                +kayttaja-jvh+ {:urakka-id             @oulun-alueurakan-2014-2019-id
                                                                 :hoitokauden-alkuvuosi 2022
                                                                 :tehtavamaarat         uudet-tehtavat})) "Hoidon urakassa ei tallenneta tehtävä- ja määräluetteloa.")

  (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tehtavamaarat
                                                +kayttaja-jvh+ {:urakka-id             @oulun-alueurakan-2014-2019-id
                                                                 :hoitokauden-alkuvuosi 2022
                                                                 :tehtavamaarat         virheellinen-tehtava})) "Vain validit tehtävät voi tallentaa."))