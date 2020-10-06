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
  [{:tehtava-id 1430 :maara 111 }
   {:tehtava-id 1414 :maara 222 }
   {:tehtava-id 3041 :maara 666.6 }
   {:tehtava-id 3009 :maara 444 }])

(def uudet-tehtavat
  [{:tehtava-id 1428 :maara 555 }
   {:tehtava-id 2991 :maara 666 }
   {:tehtava-id 2992 :maara 7.77 }
   {:tehtava-id 3004 :maara 88.8 }
   {:tehtava-id 1429 :maara 999 }
   {:tehtava-id 3021 :maara 666 }])

(def uuden-hoitokauden-tehtavat
  [{:tehtava-id 2992 :maara 6.66 }
   {:tehtava-id 1430 :maara 999 }])

(def virheellinen-tehtava
  [{:tehtava-id 2992 :maara 6.66 }
   {:tehtava-id 666 :maara 999 }])

;; TODO: hae urkakkanumerot älä kovakoodaa, muuta käyttäjä urakanvalvojaksi


;; jos tehtävähierarkian tehtävien tiedoissa tapahtuu muutoksia, tämä testi feilaa ja täytyy päivittää
(deftest tehtavahierarkian-haku
  (let [hierarkia (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tehtavahierarkia +kayttaja-jvh+
                                  {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id})]
    (is (= (count hierarkia) 104) "Hierarkiassa on 104 osaa.")
    (is (= (:tehtava (first (filter #(= 4579 (:tehtava-id %)) hierarkia))) "Rumpujen tarkastus") "Tehtävähierarkiassa palautuu tietoja.")))



(deftest tehtavaryhmat-ja-toimenpiteet-testi
  (let [tehtavaryhmat-toimenpiteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tehtavaryhmat-ja-toimenpiteet
                                                   +kayttaja-jvh+
                                                   {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id})]
    (is (= tehtavaryhmat-ja-toimenpiteet false) "Palauttaa tehtäväryhmä ja toimenpidelistan")
    (is (= true false) "Tyhjä lista jos ei löydy urakkaa")
    (is (thrown? IllegalArgumentException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :tehtavaryhmat-ja-toimenpiteet
                                                          +kayttaja-jvh+
                                                          {})) "Virhe jos ei parametria")))


; käyttää ennakkoon tallennettua testidataa
(deftest tallenna-tehtavamaarat-testi
  (let [tehtavamaarat-ja-hierarkia (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tehtavamaarat-hierarkiassa +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                               :hoitokauden-alkuvuosi 2020})
        tehtavamaarat (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                     :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-kannassa (ffirst (q (str "SELECT count(*)
                                                             FROM urakka_tehtavamaara
                                                            WHERE \"hoitokauden-alkuvuosi\" = 2020 AND urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id)))

        tehtavamaarat-ennen-paivitysta (kutsu-palvelua (:http-palvelin jarjestelma)
                                                       :tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                      :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-paivita (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                      :hoitokauden-alkuvuosi 2020
                                                                                      :tehtavamaarat         paivitettavat-olemassaolevat-tehtavat})
        tehtavamaarat-paivityksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                         :hoitokauden-alkuvuosi 2020})
        tehtavahierarkia-paivityksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                             :tehtavamaarat-hierarkiassa +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                                         :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-lisaa (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                    :hoitokauden-alkuvuosi 2020
                                                                                    :tehtavamaarat         uudet-tehtavat})
        tehtavamaarat-lisayksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                       :hoitokauden-alkuvuosi 2020})
        hoitokausi-2022-lisaa (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                      :hoitokauden-alkuvuosi 2022
                                                                                      :tehtavamaarat         uuden-hoitokauden-tehtavat})
        hoitokausi-2022 (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                       :hoitokauden-alkuvuosi 2022})
        hoitokausi-2020 (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                       :hoitokauden-alkuvuosi 2020})]

    ;; tehtävähierarkia
    (is (= (count tehtavamaarat-ja-hierarkia) 118) "Hierarkiassa on 118 osaa.")
    (is (= (:maara (first (filter #(and (= 4579 (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat-ja-hierarkia))) 32.6M) "Hoitokauden tehtävämäärä palautuu oikein hierarkiassa.")

    ;; tehtävämäärä
    (is (= (count tehtavamaarat) tehtavamaarat-kannassa) "Palutuneiden rivien lukumäärä vastaa kantaan tallennettuja.")
    (is (= (:maara (first (filter #(and (= 4579 (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat))) 32.6M) "Hoitokauden tehtävämäärä palautuu oikein.")

    ;; hoitokauden tietojen päivitys
    (is (= (count tehtavamaarat-ennen-paivitysta) (count tehtavamaarat-paivityksen-jalkeen)) "Rivejä ei lisätty, kun tietoja päivitettiin.")
    (is (= tehtavamaarat-paivita tehtavahierarkia-paivityksen-jalkeen) "Tallennusfunktio palauttaa vastauksena kannan tilan samanlaisena kuin erillinen hierarkianhakufunktio.")
    (is (= (:maara (first (filter #(and (= 4579 (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat-paivita))) 666.6M) "Päivitys päivitti määrän.")

    ;; hoitokauden tietojen lisäys
    (is (= (count tehtavamaarat-lisayksen-jalkeen) 10) "Uudet rivit lisättiin, vanhat säilyivät.")
    (is (= (:maara (first (filter #(and (= 4561 (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat-lisaa))) 666M) "Lisäys lisäsi määrän.")

    ;; uuden hoitokauden lisäys
    (is (= (count hoitokausi-2022-lisaa) 118) "Uuden hoitokauden hierarkiassa palautuu oikea määrä tehtäviä.")
    (is (= (count hoitokausi-2022) 2) "Uudet rivit lisättiin oikealle hoitokaudelle.")
    (is (= (:maara (first (filter #(and (= 4589 (:tehtava-id %))
                                        (= 2022 (:hoitokauden-alkuvuosi %))
                                        (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) hoitokausi-2022-lisaa))) 6.66M) "Uuden hoitokauden tiedot palautettiin hierarkiassa.")
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

(deftest tehtavahierarkian-haku-maarineen-testi
  (let [tehtavat-ja-maarat (kutsu-palvelua
                             (:http-palvelin jarjestelma)
                             :tehtavamaarat-hierarkiassa
                             +kayttaja-jvh+
                             {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                              :hoitokauden-alkuvuosi 2020})
        tehtavat-ja-maarat-urakan-ulkopuolelta (kutsu-palvelua
                                                 (:http-palvelin jarjestelma)
                                                 :tehtavamaarat-hierarkiassa
                                                 +kayttaja-jvh+
                                                 {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                  :hoitokauden-alkuvuosi 2028})
        tehtavat-ja-maarat-kaikki (kutsu-palvelua
                                    (:http-palvelin jarjestelma)
                                    :tehtavamaarat-hierarkiassa
                                    +kayttaja-jvh+
                                    {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                     :hoitokauden-alkuvuosi :kaikki})
        tehtavat-ja-maarat-ei-urakkaa (kutsu-palvelua
                                        (:http-palvelin jarjestelma)
                                        :tehtavamaarat-hierarkiassa
                                        +kayttaja-jvh+
                                        {:urakka-id             904569045
                                         :hoitokauden-alkuvuosi 2020})]
    (is (true? (every? #(= 2020 (:hoitokauden-alkuvuosi %)) (filter #(not (nil? (:hoitokauden-alkuvuosi %))) tehtavat-ja-maarat))) "Palauttaa tehtavahiearkian määrineen vuodelle")
    (is (empty? (filter #(and
                           (not= 0 (:maara %))
                           (some? (:maara %))) tehtavat-ja-maarat-urakan-ulkopuolelta)) "Urakan ulkopuolella ei löydy määriä")
    (is (true? (let [maaralliset (filter #(not (nil? (:hoitokauden-alkuvuosi %))) tehtavat-ja-maarat-kaikki)]
             (and (some #(= 2020 (:hoitokauden-alkuvuosi %)) maaralliset)
                  (some #(= 2022 (:hoitokauden-alkuvuosi %)) maaralliset)))) "Palauttaa kaikki määrät")
    (is (every? #(and (nil? (:urakka %))
                      (nil? (:hoitokauden-alkuvuosi %))
                      (or (= 0 (:maara %))
                          (nil? (:maara %)))) tehtavat-ja-maarat-ei-urakkaa) "Tietoja ei löydy, jos ei urakkaa")
    (is (thrown? IllegalArgumentException (kutsu-palvelua
                                            (:http-palvelin jarjestelma)
                                            :tehtavamaarat-hierarkiassa
                                            +kayttaja-jvh+
                                            {})) "Virhe jos ei parametria")))