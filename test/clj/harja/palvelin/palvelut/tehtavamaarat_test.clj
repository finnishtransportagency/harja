(ns harja.palvelin.palvelut.tehtavamaarat_test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.tehtavamaarat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [harja.kyselyt.tehtavamaarat :as tehtavamaarat]
            [harja.domain.urakka :as urakka]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [clojure.set :as set]))


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

;; Olemassaolevat tehtävät
(def id-palteiden-poisto (hae-toimenpidekoodin-id "Päällystettyjen teiden palteiden poisto" "23116"))
(def id-opastustaulut (hae-toimenpidekoodin-id
                        "Opastustaulujen ja liikennemerkkien rakentaminen tukirakenteineen (sis. liikennemerkkien poistamisia)"
                        "23116"))
(def id-K2 (hae-toimenpidekoodin-id "K2" "23104"))
(def id-ib-rampit (hae-toimenpidekoodin-id "Ib rampit" "23104"))
(def rumpujen-tarkastus (hae-toimenpidekoodin-id "Rumpujen tarkastus" "23116"))

;; Uudet lisättävät tehtävät
(def id-ise-rampit (hae-toimenpidekoodin-id "Ise rampit" "23104"))
(def id-suolaus (hae-toimenpidekoodin-id "Suolaus" "23104"))
(def id-portaiden-talvihuolto (hae-toimenpidekoodin-id "Portaiden talvihoito" "23104"))
(def id-yksityisten-rumpujen (hae-toimenpidekoodin-id "Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet" "20191"))
(def id-ic-rampit (hae-toimenpidekoodin-id "Ic rampit" "23104"))
(def id-kalium (hae-toimenpidekoodin-id "Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)" "23104"))

;; Tehtäviä, joilla sopimuksen tehtävämäärä
(def id-III (hae-toimenpidekoodin-id "III" "23104"))
(def id-katupolyn-sidonta (hae-toimenpidekoodin-id "Katupölynsidonta" "23116"))
(def id-soratoiden-polynsidonta (hae-toimenpidekoodin-id "Sorateiden pölynsidonta" "23124"))
(def id-id-ohituskaistat (hae-toimenpidekoodin-id "Is ohituskaistat" "23104"))


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
                                                         :hoitokauden-alkuvuosi 2020})
        hae-tehtava (fn [tehtava urakka maarat]
                      (first (filter 
                           #(and 
                              (= tehtava (:id %))
                              (= urakka (:urakka %))) 
                           (apply concat (mapv :tehtavat maarat)))))]
    ;; tehtävähierarkia
    #_(is (= (count tehtavamaarat-ja-hierarkia) 115) "Hierarkiassa on 115 osaa.")
    (let [tehtava (hae-tehtava id-palteiden-poisto @oulun-maanteiden-hoitourakan-2019-2024-id tehtavamaarat-ja-hierarkia)] 
      (is (=  (get-in tehtava [:maarat 2020]) 33.4M) "Hoitokauden tehtävämäärä palautuu oikein hierarkiassa."))

    ;; tehtävämäärä
    (is (= (count tehtavamaarat) tehtavamaarat-kannassa) "Palutuneiden rivien lukumäärä vastaa kantaan tallennettuja.")
    (is (= (:maara (first (filter #(and (= id-K2 (:tehtava-id %))
                                     (= 2020 (:hoitokauden-alkuvuosi %))
                                     (= @oulun-maanteiden-hoitourakan-2019-2024-id (:urakka %))) tehtavamaarat))) 55.5M) "Hoitokauden tehtävämäärä palautuu oikein.")

    ;; hoitokauden tietojen päivitys
    (is (= (count tehtavamaarat-ennen-paivitysta) (count tehtavamaarat-paivityksen-jalkeen)) "Rivejä ei lisätty, kun tietoja päivitettiin.")
    (let [poista-sisaiset-idt #(let [vanhempi-key-poistettu (update % 
                                                              :tehtavat 
                                                              (fn [t] 
                                                                (mapv 
                                                                  (fn [r] 
                                                                    (dissoc r :vanhempi)) 
                                                                  t)))] 
                                 (dissoc 
                                   vanhempi-key-poistettu
                                   :id))] 
      (is (= (mapv poista-sisaiset-idt tehtavamaarat-paivita) (mapv poista-sisaiset-idt tehtavahierarkia-paivityksen-jalkeen)) "Tallennusfunktio palauttaa vastauksena kannan tilan samanlaisena kuin erillinen hierarkianhakufunktio."))
    (let [tehtava (hae-tehtava id-palteiden-poisto @oulun-maanteiden-hoitourakan-2019-2024-id tehtavamaarat-paivita) ] 
      (is (= (get-in tehtava [:maarat 2020]) 666.7M) "Päivitys päivitti määrän."))

    ;; hoitokauden tietojen lisäys
    (is (= (count tehtavamaarat-lisayksen-jalkeen) 19) "Uudet rivit lisättiin, vanhat säilyivät.")
    
    (let [tehtava (hae-tehtava id-ise-rampit @oulun-maanteiden-hoitourakan-2019-2024-id tehtavamaarat-lisaa)] 
      (is (= (get-in tehtava [:maarat 2020]) 666M) "Lisäys lisäsi määrän."))

    ;; uuden hoitokauden lisäys
    #_(is (= (count hoitokausi-2022-lisaa) 115) "Uuden hoitokauden hierarkiassa palautuu oikea määrä tehtäviä.")
    (is (= (count hoitokausi-2022) 2) "Uudet rivit lisättiin oikealle hoitokaudelle.")
    (let [tehtava (hae-tehtava id-ib-rampit @oulun-maanteiden-hoitourakan-2019-2024-id hoitokausi-2022-lisaa) ]                           
      (is (and 
            (contains? (:maarat tehtava) 2022)
            (= 
              (get-in tehtava [:maarat 2022]) 6.66M)) "Uuden hoitokauden tiedot palautettiin hierarkiassa."))
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
    (is (true? (let [maaralliset (filter 
                                   #(some? (:maarat %)) 
                                   (apply concat (mapv :tehtavat tehtavat-ja-maarat-kaikki)))]
                 
                 (and (some #(contains? (:maarat %) 2020) maaralliset)
                      (some #(contains? (:maarat %) 2022) maaralliset)))) "Palauttaa kaikki määrät")
    (is (every? #(and (nil? (:urakka %))
                      (nil? (:hoitokauden-alkuvuosi %))
                      (or (= 0 (:maara %))
                          (nil? (:maara %)))) tehtavat-ja-maarat-ei-urakkaa) "Tietoja ei löydy, jos ei urakkaa")
    (is (thrown? IllegalArgumentException (kutsu-palvelua
                                            (:http-palvelin jarjestelma)
                                            :tehtavamaarat-hierarkiassa
                                            +kayttaja-jvh+
                                            {})) "Virhe jos ei parametria")))

(deftest tallenna-sopimuksen-tehtavamaara-testi
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-sopimuksen-tehtavamaara
                  +kayttaja-jvh+
                  {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                   :tehtava-id id-ise-rampit
                   :maara 1234M})
        odotettu {::urakka/id 35
                  ::toimenpidekoodi/id id-ise-rampit
                  ::tehtavamaarat/maara 1234M
                  ::muokkaustiedot/muokkaaja-id (:id +kayttaja-jvh+)}]
    (is (=
          (dissoc vastaus ::muokkaustiedot/muokattu ::tehtavamaarat/sopimus-tehtavamaara-id)
          odotettu))))

(deftest muokkaa-sopimuksen-tehtavamaaraa-testi
  (let [muokattava (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-sopimuksen-tehtavamaara
                      +kayttaja-jvh+
                      {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                       :tehtava-id id-suolaus
                       :maara 1234M})
        muokattu (kutsu-palvelua (:http-palvelin jarjestelma)
                   :tallenna-sopimuksen-tehtavamaara
                   +kayttaja-jvh+
                   {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                    :tehtava-id id-suolaus
                    :maara 9001M})
        odotettu {::urakka/id 35
                  ::toimenpidekoodi/id id-suolaus
                  ::tehtavamaarat/maara 9001M
                  ::muokkaustiedot/muokkaaja-id (:id +kayttaja-jvh+)}
        _ []]
    (is (=
          (dissoc muokattu ::muokkaustiedot/muokattu ::tehtavamaarat/sopimus-tehtavamaara-id)
          odotettu))
    (is (=
          (::tehtavamaarat/sopimus-tehtavamaara-id muokattava)
          (::tehtavamaarat/sopimus-tehtavamaara-id muokattu)))))

(deftest sopimuksen-tehtavamaara-vaara-tehtava-testi
  (let [vastaus (try (kutsu-palvelua (:http-palvelin jarjestelma)
                       :tallenna-sopimuksen-tehtavamaara
                       +kayttaja-jvh+
                       {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                        :tehtava-id 666
                        :maara 1234M})
                     (catch Exception e e))]
    (is (= IllegalArgumentException (type vastaus)))
    (is (= "Tehtävälle 666 ei voi suunnitella määrätietoja." (ex-message vastaus)))))

(deftest sopimuksen-tehtavamaara-vaara-urakka-testi
  (let [kemin-alueurakka-id @kemin-alueurakan-2019-2023-id
        vastaus (try (kutsu-palvelua (:http-palvelin jarjestelma)
                       :tallenna-sopimuksen-tehtavamaara
                       +kayttaja-jvh+
                       {:urakka-id kemin-alueurakka-id
                        :tehtava-id id-suolaus
                        :maara 1234M})
                     (catch Exception e e))]
    (is (= IllegalArgumentException (type vastaus)))
    (is (=
          (str "Urakka " kemin-alueurakka-id " on tyyppiä: :paallystys. "
            "Urakkatyypissä ei suunnitella tehtävä- ja määräluettelon tietoja.")
          (ex-message vastaus)))))

(deftest sopimuksen-tehtavamaarat-haku-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tehtavamaarat-hierarkiassa
                  +kayttaja-jvh+
                  {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                   :hoitokauden-alkuvuosi :kaikki})
        odotettuja-tehtavia #{id-opastustaulut id-palteiden-poisto id-ib-rampit id-K2 id-III id-katupolyn-sidonta id-soratoiden-polynsidonta id-id-ohituskaistat}
        loydetyt-tehtavat (set 
                            (map :id 
                              (filter 
                                (comp not nil? :sopimuksen-tehtavamaara) 
                                (apply concat (mapv :tehtavat vastaus)))))
        hae-tehtavan-maara-fn (fn [tehtava]
                                (:sopimuksen-tehtavamaara
                                 (first 
                                   (filter 
                                     (fn [r] 
                                       (= tehtava (:id r))) 
                                     (apply concat (mapv :tehtavat vastaus))))))]
    (is (set/subset? odotettuja-tehtavia loydetyt-tehtavat) "Kaikkien odotettujen tehtävien pitäisi olla mukana kirjatuissa sopimuksen tehtävämäärissä.")
    (is (= 25000M (hae-tehtavan-maara-fn id-opastustaulut)))
    (is (= 1000M (hae-tehtavan-maara-fn id-K2)))))
