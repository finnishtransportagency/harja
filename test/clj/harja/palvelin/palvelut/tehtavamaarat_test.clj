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


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

; testidatan id:itä
(def id-ise-rampit 2991)
(def id-ib-rampit 1446)
(def id-suolaus 1369)
(def id-palteiden-poisto 1414)
(def id-portaiden-talvihuolto 3004)
(def id-opastustaulut 1430)
(def id-yksityisten-rumpujen 3021)
(def id-ic-rampit 2995)
(def id-K2 1441)
(def id-kalium 3000)

(def rumpujen-tarkastus 3020)


(def paivitettavat-olemassaolevat-tehtavat
  [{:tehtava-id id-opastustaulut :maara 111}
   {:tehtava-id id-palteiden-poisto :maara 666.7}
   {:tehtava-id id-K2 :maara 666.6}
   {:tehtava-id id-ib-rampit :maara 444}])

(def uudet-tehtavat
  [{:tehtava-id id-kalium :maara 555}
   {:tehtava-id id-ise-rampit :maara 666}
   {:tehtava-id id-suolaus :maara 7.77}
   {:tehtava-id id-portaiden-talvihuolto :maara 88.8}
   {:tehtava-id id-ic-rampit :maara 999}
   {:tehtava-id id-yksityisten-rumpujen :maara 666}])

(def uuden-hoitokauden-tehtavat
  [{:tehtava-id id-ib-rampit :maara 6.66}
   {:tehtava-id id-opastustaulut :maara 999}])

(def virheellinen-tehtava
  [{:tehtava-id id-ib-rampit :maara 6.66}
   {:tehtava-id 666 :maara 999}])

;; TODO: hae urkakkanumerot älä kovakoodaa, muuta käyttäjä urakanvalvojaksi


;; jos tehtävähierarkian tehtävien tiedoissa tapahtuu muutoksia, tämä testi feilaa ja täytyy päivittää
(deftest tehtavahierarkian-haku
  (let [hierarkia (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tehtavahierarkia
                                  +kayttaja-jvh+
                                  {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id})]
    #_(is (= (count hierarkia) 108) "Hierarkiassa on 108 osaa.")
    (is (= (:tehtava (first (filter #(= rumpujen-tarkastus (:tehtava-id %)) hierarkia))) "Rumpujen tarkastus") "Tehtävähierarkiassa palautuu tietoja.")))



(deftest tehtavaryhmat-ja-toimenpiteet-testi
  (let [tr-tp-lkm (ffirst
                    (q (str "SELECT count(distinct tr3.id)
                             FROM tehtavaryhma tr1
                             JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
                             JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
                             LEFT JOIN toimenpidekoodi tpk4
                             ON tr3.id = tpk4.tehtavaryhma and tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                             tpk4.poistettu is not true AND tpk4.piilota is not true
                             JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
                             JOIN toimenpideinstanssi tpi on tpi.toimenpide = tpk3.id and tpi.urakka = "
                            @oulun-maanteiden-hoitourakan-2019-2024-id
                            "WHERE tr1.emo is null")))
        tehtavaryhmat-toimenpiteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tehtavaryhmat-ja-toimenpiteet
                                                   +kayttaja-jvh+
                                                   {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id})
        tehtavaryhmat-ja-toimenpiteet-vaara-urakka-id (kutsu-palvelua (:http-palvelin jarjestelma)
                                                                      :tehtavaryhmat-ja-toimenpiteet
                                                                      +kayttaja-jvh+
                                                                      {:urakka-id 36565345})]
    #_(is (= (count tehtavaryhmat-toimenpiteet) tr-tp-lkm) "Palauttaa tehtäväryhmä ja toimenpidelistan")
    (is (empty? tehtavaryhmat-ja-toimenpiteet-vaara-urakka-id) "Tyhjä lista jos ei löydy urakkaa")
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
    #_(is (= (count tehtavamaarat-ja-hierarkia) 115) "Hierarkiassa on 115 osaa.")
    (is (= (:maara (first (filter #(and (= id-palteiden-poisto (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat-ja-hierarkia))) 33.4M) "Hoitokauden tehtävämäärä palautuu oikein hierarkiassa.")

    ;; tehtävämäärä
    (is (= (count tehtavamaarat) tehtavamaarat-kannassa) "Palutuneiden rivien lukumäärä vastaa kantaan tallennettuja.")
    (is (= (:maara (first (filter #(and (= id-K2 (:tehtava-id %))
                                       (= 2020 (:hoitokauden-alkuvuosi %))
                                       (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat))) 55.5M) "Hoitokauden tehtävämäärä palautuu oikein.")

    ;; hoitokauden tietojen päivitys
    (is (= (count tehtavamaarat-ennen-paivitysta) (count tehtavamaarat-paivityksen-jalkeen)) "Rivejä ei lisätty, kun tietoja päivitettiin.")
    (is (= tehtavamaarat-paivita tehtavahierarkia-paivityksen-jalkeen) "Tallennusfunktio palauttaa vastauksena kannan tilan samanlaisena kuin erillinen hierarkianhakufunktio.")
    (is (= (:maara (first (filter #(and (= id-palteiden-poisto (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat-paivita))) 666.7M) "Päivitys päivitti määrän.")

    ;; hoitokauden tietojen lisäys
    (is (= (count tehtavamaarat-lisayksen-jalkeen) 19) "Uudet rivit lisättiin, vanhat säilyivät.")
    (is (= (:maara (first (filter #(and (= id-ise-rampit (:tehtava-id %))
                                        (= 2020 (:hoitokauden-alkuvuosi %))
                                        (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat-lisaa))) 666M) "Lisäys lisäsi määrän.")

    ;; uuden hoitokauden lisäys
    #_(is (= (count hoitokausi-2022-lisaa) 115) "Uuden hoitokauden hierarkiassa palautuu oikea määrä tehtäviä.")
    (is (= (count hoitokausi-2022) 2) "Uudet rivit lisättiin oikealle hoitokaudelle.")
    (is (= (:maara (first (filter #(and (= id-ib-rampit (:tehtava-id %))
                                       (= 2022 (:hoitokauden-alkuvuosi %))
                                       (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) hoitokausi-2022-lisaa))) 6.66M) "Uuden hoitokauden tiedot palautettiin hierarkiassa.")
    (is (= (:maara (first (filter #(= id-opastustaulut (:tehtava-id %)) hoitokausi-2022))) 999M) "Uuden hoitokauden tehtävässä on oikea määrä.")
    (is (= (:maara (first (filter #(= id-opastustaulut (:tehtava-id %)) hoitokausi-2020))) 111M) "Uuden hoitokauden lisäys ei päivittänyt vanhaa hoitokautta.")))



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
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                            :hoitokauden-alkuvuosi 2022
                                            :tehtavamaarat         uuden-hoitokauden-tehtavat})
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
