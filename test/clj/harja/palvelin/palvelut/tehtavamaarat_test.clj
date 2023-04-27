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
            [harja.domain.tehtavamaarat :as tm-domain]
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
                                         (->Tehtavamaarat false)
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
                        "Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)"
                        "23116"))
(def id-K2 (hae-toimenpidekoodin-id "K2" "23104"))
(def id-ib-rampit (hae-toimenpidekoodin-id "Ib rampit" "23104"))
(def rumpujen-korjaus (hae-toimenpidekoodin-id "Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm" "20191"))

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
(def id-soratoiden-polynsidonta (hae-toimenpidekoodin-id "Sorateiden pölynsidonta (materiaali)" "23124"))
(def id-id-ohituskaistat (hae-toimenpidekoodin-id "Is ohituskaistat" "23104"))


(def paivitettavat-olemassaolevat-tehtavat
  [{:tehtava-id id-opastustaulut :maara 111 :hoitokauden-alkuvuosi 2020}
   {:tehtava-id id-palteiden-poisto :maara 666.7 :hoitokauden-alkuvuosi 2020}
   {:tehtava-id id-K2 :maara 666.6 :hoitokauden-alkuvuosi 2020}
   {:tehtava-id id-ib-rampit :maara 444 :hoitokauden-alkuvuosi 2020}])

(def uudet-tehtavat
  [{:tehtava-id id-kalium :maara 555 :hoitokauden-alkuvuosi 2020}
   {:tehtava-id id-ise-rampit :maara 666 :hoitokauden-alkuvuosi 2020}
   {:tehtava-id id-suolaus :maara 7.77 :hoitokauden-alkuvuosi 2020}
   {:tehtava-id id-portaiden-talvihuolto :maara 88.8 :hoitokauden-alkuvuosi 2020}
   {:tehtava-id id-ic-rampit :maara 999 :hoitokauden-alkuvuosi 2020}
   {:tehtava-id id-yksityisten-rumpujen :maara 666 :hoitokauden-alkuvuosi 2020}])

(def uuden-hoitokauden-tehtavat
  [{:tehtava-id id-portaiden-talvihuolto :maara 6.66 :hoitokauden-alkuvuosi 2022}
   {:tehtava-id id-opastustaulut :maara 999 :hoitokauden-alkuvuosi 2022}])

(def virheellinen-tehtava
  [{:tehtava-id id-ib-rampit :maara 6.66}
   {:tehtava-id 666 :maara 999}])


(deftest tehtavaryhmat-ja-toimenpiteet-testi
  (let [tr-tp-lkm (ffirst
                    (q (str "SELECT count(distinct tr3.id)
                               FROM tehtavaryhma tr3
                                    LEFT JOIN toimenpidekoodi tpk4 ON tr3.id = tpk4.tehtavaryhma
                                            AND tpk4.taso = 4 AND tpk4.ensisijainen is true
                                            AND tpk4.poistettu is not true AND tpk4.piilota is not true
                                    JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
                                    JOIN toimenpideinstanssi tpi on tpi.toimenpide = tpk3.id and tpi.urakka = "
                         @oulun-maanteiden-hoitourakan-2019-2024-id
                         "WHERE tr3.tyyppi = 'alataso' AND (tr3.yksiloiva_tunniste IS NULL\n                                                 OR (tr3.yksiloiva_tunniste IS NOT NULL AND tr3.yksiloiva_tunniste != '0e78b556-74ee-437f-ac67-7a03381c64f6'))")))
        tehtavaryhmat-toimenpiteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :tehtavaryhmat-ja-toimenpiteet
                                     +kayttaja-jvh+
                                     {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id})
        ;; Tehtäväryhmälistaan ei saa lisätä kaikkia tehtäväryhmiä. Varmista, että ainakin nämä puuttuu
        kielletyt-tehtavaryhmat (some (fn [tr]
                                        (= "Tilaajan rahavaraus (T3)" (:tehtavaryhma-nimi tr)))
                                  tehtavaryhmat-toimenpiteet)
        tehtavaryhmat-ja-toimenpiteet-vaara-urakka-id (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :tehtavaryhmat-ja-toimenpiteet
                                                        +kayttaja-jvh+
                                                        {:urakka-id 36565345})]
    (is (nil? kielletyt-tehtavaryhmat))
    (is (= (count tehtavaryhmat-toimenpiteet) tr-tp-lkm) "Palauttaa tehtäväryhmä ja toimenpidelistan")
    (is (empty? tehtavaryhmat-ja-toimenpiteet-vaara-urakka-id) "Tyhjä lista jos ei löydy urakkaa")
    (is (thrown? IllegalArgumentException (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tehtavaryhmat-ja-toimenpiteet
                                            +kayttaja-jvh+
                                            {})) "Virhe jos ei parametria")))


; käyttää ennakkoon tallennettua testidataa
(deftest tallenna-tehtavamaarat-testi
  (let [tehtavamaarat-ja-hierarkia (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :tehtavamaarat-hierarkiassa +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                 :hoitokauden-alkuvuosi 2020})
        tehtavamaarat (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                       :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-kannassa (ffirst (q (str "SELECT count(*)
                                                             FROM urakka_tehtavamaara
                                                            WHERE \"hoitokauden-alkuvuosi\" = 2020 AND urakka = " @oulun-maanteiden-hoitourakan-2019-2024-id)))

        tehtavamaarat-ennen-paivitysta (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                        :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-paivita (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                        :nykyinen-hoitokausi 2020
                                                                        :tehtavamaarat paivitettavat-olemassaolevat-tehtavat})
        tehtavamaarat-paivityksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                           :hoitokauden-alkuvuosi 2020})
        tehtavahierarkia-paivityksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :tehtavamaarat-hierarkiassa +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                                           :hoitokauden-alkuvuosi 2020})
        tehtavamaarat-lisaa (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                      :nykyinen-hoitokausi 2020
                                                                      :tehtavamaarat uudet-tehtavat})
        tehtavamaarat-lisayksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                         :hoitokauden-alkuvuosi 2020})
        hoitokausi-2022-lisaa (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                                        :nykyinen-hoitokausi 2022
                                                                        :tehtavamaarat uuden-hoitokauden-tehtavat})
        hoitokausi-2022 (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                                         :hoitokauden-alkuvuosi 2022})
        hoitokausi-2020 (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tehtavamaarat +kayttaja-jvh+ {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
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
      (is (= (get-in tehtava [:suunnitellut-maarat 2020]) 33.4M) "Hoitokauden tehtävämäärä palautuu oikein hierarkiassa."))

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
    (let [tehtava (hae-tehtava id-palteiden-poisto @oulun-maanteiden-hoitourakan-2019-2024-id tehtavamaarat-paivita)]
      (is (= (get-in tehtava [:suunnitellut-maarat 2020]) 666.7M) "Päivitys päivitti määrän."))

    ;; hoitokauden tietojen lisäys
    (is (= (count tehtavamaarat-lisayksen-jalkeen) 19) "Uudet rivit lisättiin, vanhat säilyivät.")

    ;; määrätieto lisättiin
    (let [tehtava (hae-tehtava id-portaiden-talvihuolto @oulun-maanteiden-hoitourakan-2019-2024-id tehtavamaarat-lisaa)]
      (is (= (get-in tehtava [:suunnitellut-maarat 2020]) 88.8M) "Lisäys lisäsi määrän."))

    ;; aluetieto muuttui
    (let [tehtava (hae-tehtava id-ise-rampit @oulun-maanteiden-hoitourakan-2019-2024-id tehtavamaarat-lisaa)]
      (is (= (get-in tehtava [:suunnitellut-maarat 2020]) 666M) "Lisäys lisäsi muuttuneen aluemaaran."))

    ;; uuden hoitokauden lisäys
    #_(is (= (count hoitokausi-2022-lisaa) 115) "Uuden hoitokauden hierarkiassa palautuu oikea määrä tehtäviä.")
    (is (= (count hoitokausi-2022) 2) "Uudet rivit lisättiin oikealle hoitokaudelle.")
    (let [tehtava (hae-tehtava id-portaiden-talvihuolto @oulun-maanteiden-hoitourakan-2019-2024-id hoitokausi-2022-lisaa)]
      (is (and
            (contains? (:suunnitellut-maarat tehtava) 2022)
            (=
              (get-in tehtava [:suunnitellut-maarat 2022]) 6.66M)) "Uuden hoitokauden tiedot palautettiin hierarkiassa."))
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
                                            :nykyinen-hoitokausi 2022
                                            :tehtavamaarat         uuden-hoitokauden-tehtavat})
  (let [tehtavat-ja-maarat (kutsu-palvelua
                             (:http-palvelin jarjestelma)
                             :tehtavamaarat-hierarkiassa
                             +kayttaja-jvh+
                             {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                              :hoitokauden-alkuvuosi 2020})]
    (is (true? (every? #(= 2020 (:hoitokauden-alkuvuosi %)) (filter #(not (nil? (:hoitokauden-alkuvuosi %))) tehtavat-ja-maarat))) "Palauttaa tehtavahiearkian määrineen vuodelle"))
  (let [tehtavat-ja-maarat-urakan-ulkopuolelta (kutsu-palvelua
                                                 (:http-palvelin jarjestelma)
                                                 :tehtavamaarat-hierarkiassa
                                                 +kayttaja-jvh+
                                                 {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                                  :hoitokauden-alkuvuosi 2028})]

       (is (empty? (filter #(and
                         (not= 0 (:maara %))
                         (some? (:maara %))) tehtavat-ja-maarat-urakan-ulkopuolelta)) "Urakan ulkopuolella ei löydy määriä"))
  (let [tehtavat-ja-maarat-kaikki (kutsu-palvelua
                                    (:http-palvelin jarjestelma)
                                    :tehtavamaarat-hierarkiassa
                                    +kayttaja-jvh+
                                    {:urakka-id             @oulun-maanteiden-hoitourakan-2019-2024-id
                                     :hoitokauden-alkuvuosi :kaikki})]

       (is (true? (let [alue-tehtavat (filter
                                 #(some? (:sopimuksen-aluetieto-maara %))
                                 (apply concat (mapv :tehtavat tehtavat-ja-maarat-kaikki)))]

               (and (some #(contains? (:sopimuksen-aluetieto-maara %) 2020) alue-tehtavat)
                 (some #(contains? (:sopimuksen-aluetieto-maara %) 2022) alue-tehtavat)))) "Palauttaa kaikki aluetieto tehtävät.")
       (is (true? (let [maara-tehtavat (filter
                                        #(some? (:sopimuksen-tehtavamaarat %))
                                        (apply concat (mapv :tehtavat tehtavat-ja-maarat-kaikki)))]

                    (and (some #(contains? (:sopimuksen-tehtavamaarat %) 2020) maara-tehtavat)
                      (some #(contains? (:sopimuksen-tehtavamaarat %) 2022) maara-tehtavat)))) "Palauttaa kaikki aluetieto tehtävät."))
  ;; Annetulla urakalla ei voi olla sopimustietoja, koska urakan id on aivan väärä
  (is (thrown? IllegalArgumentException (kutsu-palvelua
                                          (:http-palvelin jarjestelma)
                                          :tehtavamaarat-hierarkiassa
                                          +kayttaja-jvh+
                                          {:urakka-id 904569045
                                           :hoitokauden-alkuvuosi 2020})) "Urakkaa ei löydy.")
  ;; Muutakin pielessä, kuin urakan id
  (is (thrown? IllegalArgumentException (kutsu-palvelua
                                          (:http-palvelin jarjestelma)
                                          :tehtavamaarat-hierarkiassa
                                          +kayttaja-jvh+
                                          {})) "Virhe jos ei parametria"))

(deftest tallenna-sopimuksen-tehtavamaara-testi
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-sopimuksen-tehtavamaara
                  +kayttaja-jvh+
                  {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                   :tehtava-id id-yksityisten-rumpujen
                   :hoitovuosi 2021
                   :maara 1234M})
        vastaus-2021 (nth vastaus 2) ;; Tallennus palauttaa kaikkien vuosien tehtävät, joten otetaan niistä vuodelle 2021 osuva
        odotettu {::urakka/id 35
                  ::toimenpidekoodi/id id-yksityisten-rumpujen
                  ::tm-domain/maara 1234M
                  ::tm-domain/hoitovuosi 2021
                  ::muokkaustiedot/muokkaaja-id (:id +kayttaja-jvh+)}]
    (is (=
          (dissoc vastaus-2021 ::muokkaustiedot/muokattu ::tm-domain/sopimus-tehtavamaara-id)
          odotettu))))

(deftest muokkaa-sopimuksen-tehtavamaaraa-testi
  (let [muokattava (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-sopimuksen-tehtavamaara
                      +kayttaja-jvh+
                      {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                       :tehtava-id id-suolaus
                       :hoitovuosi 2022
                       :maara 1234M})
        muokattu (kutsu-palvelua (:http-palvelin jarjestelma)
                   :tallenna-sopimuksen-tehtavamaara
                   +kayttaja-jvh+
                   {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                    :tehtava-id id-suolaus
                    :hoitovuosi 2022
                    :maara 9001M})
        muokattu-2022 (nth muokattu 3) ;; Tallennus palauttaa kaikkien vuosien tehtävät, joten otetaan niistä vuodelle 2022 osuva
        odotettu {::urakka/id 35
                  ::toimenpidekoodi/id id-suolaus
                  ::tm-domain/maara 9001M
                  ::tm-domain/hoitovuosi 2022
                  ::muokkaustiedot/muokkaaja-id (:id +kayttaja-jvh+)}]
    (is (=
          (dissoc muokattu-2022 ::muokkaustiedot/muokattu ::tm-domain/sopimus-tehtavamaara-id)
          odotettu))
    (is (=
          (::tm-domain/sopimus-tehtavamaara-id muokattava)
          (::tm-domain/sopimus-tehtavamaara-id muokattu)))))

(deftest sopimuksen-tehtavamaara-vaara-tehtava-testi
  (let [vastaus (try (kutsu-palvelua (:http-palvelin jarjestelma)
                       :tallenna-sopimuksen-tehtavamaara
                       +kayttaja-jvh+
                       {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                        :tehtava-id 666
                        :maara 1234M})
                     (catch Exception e e))]
    (is (= IllegalArgumentException (type vastaus)))
    (is (= "Tehtävälle 666 ei voi antaa sopimuksessa määrätietoja." (ex-message vastaus)))))

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
            "Urakkatyypissä ei ole sopimuksella tehtävä- ja määräluettelon tietoja.")
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
                                #(or (not (nil? (:sopimuksen-tehtavamaarat %)))
                                   (not (nil? (:sopimuksen-aluetieto-maara %)))) 
                                (mapcat :tehtavat vastaus))))
        hae-tehtavan-maara-fn (fn [tehtava vuosi]
                                (let [oikea-tehtava
                                      (first 
                                        (filter 
                                          (fn [r]
                                            (= tehtava (:id r))) 
                                          (mapcat :tehtavat vastaus)))]
                                  (if (:aluetieto? oikea-tehtava)
                                    (get (:sopimuksen-aluetieto-maara oikea-tehtava) vuosi)
                                    (get (:sopimuksen-tehtavamaarat oikea-tehtava) vuosi))))]
    (is (set/subset? odotettuja-tehtavia loydetyt-tehtavat) "Kaikkien odotettujen tehtävien pitäisi olla mukana kirjatuissa sopimuksen tehtävämäärissä.")
    (is (= 25000M (hae-tehtavan-maara-fn id-opastustaulut 2019)))
    (is (= 500M (hae-tehtavan-maara-fn id-III 2019)))
    (is (= 10000M (hae-tehtavan-maara-fn id-katupolyn-sidonta 2020)))
    (is (= 1000M (hae-tehtavan-maara-fn id-K2 2019)))))
