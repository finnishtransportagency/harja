(ns harja.palvelin.palvelut.muutos-palvelu-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]

            [harja.tyokalut.yleiset :refer [round2]]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.liitteet :as liitteet-komponentti]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.kyselyt.muutos-kyselyt :as muutos-kyselyt]
            [harja.palvelin.palvelut.kulut.kulut :as kulut-palvelu])
  (:import (org.apache.commons.io IOUtils)))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :liitteiden-hallinta (component/using
                                 (liitteet-komponentti/->Liitteet nil nil nil)
                                 [:db])
          :kulut (component/using
                   (kulut-palvelu/->Kulut)
                   [:http-palvelin :db])
          :muutokset (component/using
                       (muutos-palvelu/->Muutos {:kehitysmoodi true})
                       [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)

(defn hae-urakan-muutostiedot [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :hae-urakan-muutostiedot
    kayttaja
    tiedot))

(deftest hae-urakan-kirjatut-muutokset-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        vastaus (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :valittu-hoitokausi valittu-hoitokausi})
        odotetut-kirjatut-muutokset [{:alityyppi nil
                                      :id 1
                                      :jjh-muutosten-summa nil
                                      :kulu_kohdistus nil
                                      :kustannusvaikutukset (list {:hoitokauden_alkuvuosi 2025
                                                                   :kustannuslaji "hankintakustannukset"
                                                                   :summa 1000
                                                                   :toimenpideinstanssi 90
                                                                   :versio 1})
                                      :liitteet nil
                                      :luonnos false
                                      :nimi "Päällysteen paikkausmuutos"
                                      :syy "Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot."
                                      :tavoitehinnan-muutos 1000
                                      :tehtavat_ja_maarat (list {:hoitokauden_alkuvuosi 2025
                                                                 :maaramuutos 100
                                                                 :suunniteltu_maara 0
                                                                 :tehtava 24628
                                                                 :versio 1})
                                      :tyyppi "pysyva"
                                      :urakka 36
                                      :versio 1
                                      :voimassa_alkaen #inst"2025-09-30T21:00:00.000-00:00"}
                                     {:alityyppi :erillisrahoitus
                                      :id 2
                                      :jjh-muutosten-summa nil
                                      :kulu_kohdistus nil
                                      :kustannusvaikutukset (list {:hoitokauden_alkuvuosi 2025
                                                                   :kustannuslaji "erillishankinnat"
                                                                   :summa 3000
                                                                   :toimenpideinstanssi nil
                                                                   :versio 1})
                                      :liitteet nil
                                      :luonnos false
                                      :nimi "Erillisrahoitettu sorastusmuutos"
                                      :syy "Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua."
                                      :tavoitehinnan-muutos 3000
                                      :tehtavat_ja_maarat ()
                                      :tyyppi "muutostyo"
                                      :urakka 36
                                      :versio 1
                                      :voimassa_alkaen #inst"2025-09-30T21:00:00.000-00:00"}
                                     {:alityyppi :poikkeama
                                      :id 3
                                      :jjh-muutosten-summa nil
                                      :kulu_kohdistus nil
                                      :kustannusvaikutukset (list {:hoitokauden_alkuvuosi 2025
                                                                   :kustannuslaji "hankintakustannukset"
                                                                   :summa 1000
                                                                   :toimenpideinstanssi 91
                                                                   :versio 1})
                                      :liitteet (list {:id 11
                                                       :muutos 3})
                                      :luonnos false
                                      :nimi "Tämän hoitovuoden määräpoikkeamamuutos"
                                      :syy "Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa."
                                      :tavoitehinnan-muutos 1000
                                      :tehtavat_ja_maarat (list {:hoitokauden_alkuvuosi 2025
                                                                 :maaramuutos -40
                                                                 :suunniteltu_maara nil
                                                                 :tehtava 1406
                                                                 :versio 1}
                                                            {:hoitokauden_alkuvuosi 2025
                                                             :maaramuutos -30
                                                             :suunniteltu_maara 8
                                                             :tehtava 9454
                                                             :versio 1})
                                      :tyyppi "muutostyo"
                                      :urakka 36
                                      :versio 1
                                      :voimassa_alkaen #inst"2025-09-30T21:00:00.000-00:00"}
                                     {:alityyppi nil
                                      :id 4
                                      :jjh-muutosten-summa 1230M
                                      :kulu_kohdistus nil
                                      :kustannusvaikutukset ()
                                      :liitteet nil
                                      :luonnos false
                                      :nimi nil
                                      :syy "Työmääräarviot ylittyivät"
                                      :tavoitehinnan-muutos 1230M
                                      :tehtavat_ja_maarat ()
                                      :tyyppi "johto-ja-hallintokorvaus"
                                      :urakka 36
                                      :versio 1
                                      :voimassa_alkaen #inst"2025-10-19T21:00:00.000-00:00"}]]
    (is (= (count (:kirjatut-muutokset vastaus)) 4) "oikea määrä muutoksia")
    (is (= (:kirjatut-muutokset vastaus) odotetut-kirjatut-muutokset))))


(deftest hae-urakan-rahavarausten-muutostiedot-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        odotetut-rahavarausten-muutokset
        [{:id 1, :toteumat 100000M, :nimi "Äkilliset hoitotyöt", :summa-indeksikorjattu 133200M, :tavoitehinnan-muutos -33200M}
         {:id 2, :toteumat 1000M, :nimi "Vahinkojen korjaukset", :summa-indeksikorjattu 2640M, :tavoitehinnan-muutos -1640M}
         {:id 3, :nimi "Tilaajan rahavaraus kannustinjärjestelmään", :summa-indeksikorjattu 39600M, :tavoitehinnan-muutos -39600M}
         {:id :yhteenveto, :summa-indeksikorjattu 175440M, :toteumat 101000M, :tavoitehinnan-muutos -74440M}]
        vastaus (:rahavarausten-muutokset (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                   :valittu-hoitokausi valittu-hoitokausi}))]

    (is (= (count vastaus) 4) "Rahavarausten muutokset: oikea määrä rivejä")
    (is (some #{:yhteenveto} (mapv :id vastaus)) "Rahavarausten muutokset: yhteenveto löytyy")
    (is (= vastaus odotetut-rahavarausten-muutokset) "Rahavarausten muutokset: koko lista odotettuja arvoja")))


(deftest hae-urakan-tavoitehinta-muutosten-kokonaissumma-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]

        vastaus (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :valittu-hoitokausi valittu-hoitokausi})
        budjettitavoitteet (:budjettitavoitteet vastaus)]

    ;; Indeksikorjattu tavoitehinta on nil, koska urakalle ei ole vahvistettu indeksikorjausta hoitovuodelle 2025
    (is (= nil (:tavoitehinta-indeksikorjattu budjettitavoitteet)) "Hoitovuoden alun indeksikorjattu tavoitehinta")
    (is (= {2021 false 2022 false 2023 false 2024 false 2025 false}
          (:tavoitehinta-indeksikorjattu-per-hoitovuosi budjettitavoitteet)))

    (is (= 1374.0 (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet))
      "Aiemmat pysyvät muutokset indeksikorjattuna")

    (is (= 6230M (:kirjatut-muutokset-yht budjettitavoitteet)) "Kirjatut muutokset yhteensä")

    (is (= -82258.0 (some->>
                      (:toteumiin-perustuvat-muutokset-yht budjettitavoitteet)
                      (round2 2))) "Toteutumiin perustuvat muutokset yhteensä")


    ;; Muutosten vaikutus yhteensä sisältää:
    ;; * Indeksikorjatun tavoitehinnan
    ;; * Aiemmat pysyvät muutokset (indeksikorjattuna)
    ;; * Kirjatut muutokset (tavoitehinnan muutokset) yhteensä
    ;; * Toteutumiin perustuvat muutokset (tavoitehinnan muutokset) yhteensä
    (is (= 255106.0 (some->> (:muutosten-vaikutus-yht budjettitavoitteet) (round2 2))) "Muutosten vaikutus yhteensä")))

(deftest hae-urakan-tavoitehinta-muutosten-kokonaissumma-suomussalmi
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        valittu-hoitokausi [(pvm/->pvm "1.10.2026") (pvm/->pvm "30.09.2027")]

        vastaus (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :valittu-hoitokausi valittu-hoitokausi})
        budjettitavoitteet (:budjettitavoitteet vastaus)]

    ;; Indeksikorjattu tavoitehinta on nil, koska urakalle ei ole vahvistettu indeksikorjausta hoitovuodelle 2025
    (is (= nil (:tavoitehinta-indeksikorjattu budjettitavoitteet)) "Hoitovuoden alun indeksikorjattu tavoitehinta")
    (is (= {2024 false 2025 false 2026 false 2027 false 2028 false}
          (:tavoitehinta-indeksikorjattu-per-hoitovuosi budjettitavoitteet)))
    (is (= 0 (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet))
      "Aiemmat pysyvät muutokset indeksikorjattuna")

    ;; Urakalle ei ole kirjattu muutoksia hoitovuodelle 2026-2027, ainoastaan aiemman vuoden pysyvät muutokset pitäisi näkyä
    (is (= 0 (:kirjatut-muutokset-yht budjettitavoitteet)) "Kirjatut muutokset yhteensä")

    ;; Urakalle ei ole lainkaan kirjattu toteutumiin perustuvia muutoksia
    (is (= 0.0 (some->>
                 (:toteumiin-perustuvat-muutokset-yht budjettitavoitteet)
                 (round2 2))) "Toteutumiin perustuvat muutokset yhteensä")

    ;; Muutosten vaikutus yhteensä sisältää:
    ;; * Indeksikorjatun tavoitehinnan
    ;; * Aiemmat pysyvät muutokset (indeksikorjattuna)
    ;; * Kirjatut muutokset (tavoitehinnan muutokset) yhteensä
    ;; * Toteutumiin perustuvat muutokset (tavoitehinnan muutokset) yhteensä
    (is (= 0.0 (some->> (:muutosten-vaikutus-yht budjettitavoitteet) (round2 2))) "Muutosten vaikutus yhteensä")))

(deftest hae-urakan-muutostiedot-ii-kun-annetuilla-ehdoilla-ei-loydy
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi-22-23 [(pvm/->pvm "1.10.2022") (pvm/->pvm "30.09.2023")]
        vastaus-22-23 (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :valittu-hoitokausi valittu-hoitokausi-22-23})
        valittu-hoitokausi-23-24 [(pvm/->pvm "1.10.2023") (pvm/->pvm "30.09.2024")]
        vastaus-23-24 (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :valittu-hoitokausi valittu-hoitokausi-23-24})
        valittu-hoitokausi-24-25 [(pvm/->pvm "1.10.2024") (pvm/->pvm "30.09.2025")]
        vastaus-24-25 (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :valittu-hoitokausi valittu-hoitokausi-24-25})
        odotetut-rivit-24-25 [{:alityyppi nil
                               :id 5
                               :jjh-muutosten-summa nil
                               :kulu_kohdistus nil
                               :kustannusvaikutukset ()
                               :liitteet nil
                               :luonnos false
                               :nimi "Lisää paikkausta"
                               :syy "Jonkin verran pitäisi paikkailla lisää tänä vuonna"
                               :tavoitehinnan-muutos 0
                               :tehtavat_ja_maarat ()
                               :tyyppi "pysyva"
                               :urakka 36
                               :versio 1
                               :voimassa_alkaen #inst"2024-09-30T21:00:00.000-00:00"}]]
    (is (= (count (:kirjatut-muutokset vastaus-22-23)) 0) "oikea määrä muutoksia 22-23")
    (is (= (count (:kirjatut-muutokset vastaus-23-24)) 0) "oikea määrä muutoksia 23-24")
    (is (= (count (:kirjatut-muutokset vastaus-24-25)) 1) "oikea määrä muutoksia 24-25")
    (is (= [] (:kirjatut-muutokset vastaus-22-23) (:kirjatut-muutokset vastaus-23-24)))

    ;; Pitäisi löytyä "aiemman vuoden pysyvä muutos" joka on voimassa 1.10.2024 alkaen
    (is (= odotetut-rivit-24-25 (:kirjatut-muutokset vastaus-24-25)))))


(deftest tallenna-rahavarausmuutosten-syyt-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        payload-1 [{:id 1 :syy "Tämä on syy 1"}
                   {:id 2 :syy "Tämä on syy 2"}
                   {:id 3 :syy "Tämä on syy 3"}]
        payload-2 [{:id 1 :syy "Tämä on syy 1 muokattuna"}]
        ;; ensimmäinen tallennus, muokkaaja ja muokattu tyhjiä
        vastaus-luonnin-jalkeen (get-in
                                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-rahavarausmuutosten-syyt
                                    +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :valittu-hoitokausi valittu-hoitokausi
                                     :rivit payload-1})
                                  [:rahavarausten-muutokset])
        kanta-luonnin-jalkeen (q-map (format "SELECT * FROM mhu_muutos_rahavarausmuutoksen_syy WHERE urakka = %s AND hoitokauden_alkuvuosi = %s;"
                                       urakka-id
                                       (pvm/vuosi (first valittu-hoitokausi))))
        odotettu-kanta-luonnin-jalkeen-ilman-aikaleimaa [{:hoitokauden_alkuvuosi 2025
                                                          :luoja (:id +kayttaja-jvh+)
                                                          :muokattu nil
                                                          :muokkaaja nil
                                                          :rahavaraus_id 1
                                                          :syy "Tämä on syy 1"
                                                          :urakka 36}
                                                         {:hoitokauden_alkuvuosi 2025
                                                          :luoja (:id +kayttaja-jvh+)
                                                          :muokattu nil
                                                          :muokkaaja nil
                                                          :rahavaraus_id 2
                                                          :syy "Tämä on syy 2"
                                                          :urakka 36}
                                                         {:hoitokauden_alkuvuosi 2025
                                                          :luoja (:id +kayttaja-jvh+)
                                                          :muokattu nil
                                                          :muokkaaja nil
                                                          :rahavaraus_id 3
                                                          :syy "Tämä on syy 3"
                                                          :urakka 36}]
        odotetut-luonnin-jalkeen [{:id 1
                                   :nimi "Äkilliset hoitotyöt"
                                   :summa-indeksikorjattu 133200M
                                   :syy "Tämä on syy 1"
                                   :tavoitehinnan-muutos -33200M
                                   :toteumat 100000M}
                                  {:id 2
                                   :nimi "Vahinkojen korjaukset"
                                   :summa-indeksikorjattu 2640M
                                   :syy "Tämä on syy 2"
                                   :tavoitehinnan-muutos -1640M
                                   :toteumat 1000M}
                                  {:id 3
                                   :nimi "Tilaajan rahavaraus kannustinjärjestelmään"
                                   :summa-indeksikorjattu 39600M
                                   :syy "Tämä on syy 3"
                                   :tavoitehinnan-muutos -39600M}
                                  {:id :yhteenveto
                                   :summa-indeksikorjattu 175440M
                                   :tavoitehinnan-muutos -74440M
                                   :toteumat 101000M}]
        vastaus-muokkauksen-jalkeen (get-in
                                      (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :tallenna-rahavarausmuutosten-syyt
                                        +kayttaja-jvh+
                                        {:urakka-id urakka-id
                                         :valittu-hoitokausi valittu-hoitokausi
                                         :rivit payload-2})
                                      [:rahavarausten-muutokset])
        kanta-muokkauksen-jalkeen (q-map (format "SELECT * FROM mhu_muutos_rahavarausmuutoksen_syy WHERE urakka = %s AND hoitokauden_alkuvuosi = %s;"
                                           urakka-id
                                           (pvm/vuosi (first valittu-hoitokausi))))
        odotettu-kanta-muokkauksen-jalkeen-ilman-aikaleimaa [{:hoitokauden_alkuvuosi 2025
                                                              :luoja (:id +kayttaja-jvh+)
                                                              :muokattu "tähän vaihdetaan assertissa jokin date..."
                                                              :muokkaaja (:id +kayttaja-jvh+)
                                                              :rahavaraus_id 1
                                                              :syy "Tämä on syy 1 muokattuna"
                                                              :urakka 36}
                                                             {:hoitokauden_alkuvuosi 2025
                                                              :luoja (:id +kayttaja-jvh+)
                                                              :muokattu nil
                                                              :muokkaaja nil
                                                              :rahavaraus_id 2
                                                              :syy "Tämä on syy 2"
                                                              :urakka 36}
                                                             {:hoitokauden_alkuvuosi 2025
                                                              :luoja (:id +kayttaja-jvh+)
                                                              :muokattu nil
                                                              :muokkaaja nil
                                                              :rahavaraus_id 3
                                                              :syy "Tämä on syy 3"
                                                              :urakka 36}]
        odotetut-muokkauksen-jalkeen [{:id 1
                                       :nimi "Äkilliset hoitotyöt"
                                       :summa-indeksikorjattu 133200M
                                       :syy "Tämä on syy 1 muokattuna"
                                       :tavoitehinnan-muutos -33200M
                                       :toteumat 100000M}
                                      {:id 2
                                       :nimi "Vahinkojen korjaukset"
                                       :summa-indeksikorjattu 2640M
                                       :syy "Tämä on syy 2"
                                       :tavoitehinnan-muutos -1640M
                                       :toteumat 1000M}
                                      {:id 3
                                       :nimi "Tilaajan rahavaraus kannustinjärjestelmään"
                                       :summa-indeksikorjattu 39600M
                                       :syy "Tämä on syy 3"
                                       :tavoitehinnan-muutos -39600M}
                                      {:id :yhteenveto
                                       :summa-indeksikorjattu 175440M
                                       :tavoitehinnan-muutos -74440M
                                       :toteumat 101000M}]]
    ;; assertoidaan luodut, näistä löytyy muokkausmetatiedoista vain luoja ja luotu
    (is (= vastaus-luonnin-jalkeen odotetut-luonnin-jalkeen) "Rahavarausmuutosten syyt luonnin jälkeen")
    (is (= (map #(dissoc % :luotu) kanta-luonnin-jalkeen) (map #(dissoc % :luotu) odotettu-kanta-luonnin-jalkeen-ilman-aikaleimaa)) "Rahavarausmuutosten syyt kannasta luonnin jälkeen")
    (is (every? #(instance? java.util.Date (:luotu %)) kanta-luonnin-jalkeen) ":luotu on date")

    ;; assertoidaan muokatut, näistä löytyy id:llä 1 myös muokattu ja muokkaaja
    (is (= (map #(dissoc % :luotu :muokattu) kanta-muokkauksen-jalkeen) (map #(dissoc % :luotu :muokattu) odotettu-kanta-muokkauksen-jalkeen-ilman-aikaleimaa)) "Rahavarausmuutosten syyt kannasta muokkauksen jälkeen")
    (is (instance? java.util.Date (:muokattu (first (filter #(= (:rahavaraus_id %) 1) kanta-muokkauksen-jalkeen)))) "Muokatun syyn muokkausaika on asetettu")
    (is (= vastaus-muokkauksen-jalkeen odotetut-muokkauksen-jalkeen) "Rahavarausmuutosten syyt muokkauksen jälkeen")))

(deftest tallenna-johto-ja-hallintokorvausmuutos-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        muutos-payload {:voimassa_alkaen #inst "2025-10-20T10:07:32.000-00:00",
                        :syy "Johtamisen tarve muuttui",
                        :kulut (list {:pvm #inst "2025-10-14T21:00:00.000-00:00", :tavoitehinnan-muutos 10}
                                 {:pvm #inst "2025-11-14T22:00:00.000-00:00", :tavoitehinnan-muutos 20}
                                 {:pvm #inst "2025-12-14T22:00:00.000-00:00", :tavoitehinnan-muutos 30}
                                 {:pvm #inst "2026-01-14T22:00:00.000-00:00", :tavoitehinnan-muutos 40}
                                 {:pvm #inst "2026-02-14T22:00:00.000-00:00", :tavoitehinnan-muutos 50}
                                 {:pvm #inst "2026-03-14T22:00:00.000-00:00", :tavoitehinnan-muutos 60}
                                 {:pvm #inst "2026-04-14T21:00:00.000-00:00", :tavoitehinnan-muutos 70}
                                 {:pvm #inst "2026-05-14T21:00:00.000-00:00", :tavoitehinnan-muutos 80}
                                 {:pvm #inst "2026-06-14T21:00:00.000-00:00", :tavoitehinnan-muutos 90}
                                 {:pvm #inst "2026-07-14T21:00:00.000-00:00", :tavoitehinnan-muutos 100}
                                 {:pvm #inst "2026-08-14T21:00:00.000-00:00", :tavoitehinnan-muutos 110}
                                 {:pvm #inst "2026-09-14T21:00:00.000-00:00", :tavoitehinnan-muutos 120}),
                        :tyyppi "johto-ja-hallintokorvaus"}
        max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
        vastaus-luonnin-jalkeen (filter
                                  #(= "Johtamisen tarve muuttui" (:syy %))
                                  (:kirjatut-muutokset
                                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-muutos
                                      +kayttaja-jvh+
                                      {:urakka-id urakka-id
                                       :valittu-hoitokausi valittu-hoitokausi
                                       :muutos muutos-payload})))
        historia-tyhja-insertin-jalkeen (first (q (format "SELECT * FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        odotetut-luonnin-jalkeen (list {:id (inc max-id-ennen-tallennusta)
                                        :jjh-muutosten-summa 780M
                                        :kulu_kohdistus nil
                                        :kustannusvaikutukset (list)
                                        :liitteet nil
                                        :luonnos nil
                                        :nimi nil
                                        :syy "Johtamisen tarve muuttui"
                                        :tavoitehinnan-muutos 780M
                                        :tehtavat_ja_maarat (list)
                                        :tyyppi "johto-ja-hallintokorvaus"
                                        :alityyppi nil
                                        :urakka urakka-id
                                        :versio 1
                                        :voimassa_alkaen #inst"2025-10-19T21:00:00.000-00:00"})
        ;; sitten päivitetään samaa muutosta, jolloin tulee rivi historiatietoon...
        vastaus-updaten-jalkeen (filter
                                  #(= "Johtamisen tarve muuttui taas" (:syy %))
                                  (:kirjatut-muutokset
                                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-muutos
                                      +kayttaja-jvh+
                                      {:urakka-id urakka-id
                                       :valittu-hoitokausi valittu-hoitokausi
                                       :muutos (assoc muutos-payload
                                                 :id (inc max-id-ennen-tallennusta)
                                                 :syy "Johtamisen tarve muuttui taas")})))
        odotetut-updaten-jalkeen (list {:id (inc max-id-ennen-tallennusta)
                                        :jjh-muutosten-summa 780M
                                        :kulu_kohdistus nil
                                        :kustannusvaikutukset (list)
                                        :liitteet nil
                                        :luonnos nil
                                        :nimi nil
                                        :syy "Johtamisen tarve muuttui taas"
                                        :tavoitehinnan-muutos 780M
                                        :tehtavat_ja_maarat (list)
                                        :tyyppi "johto-ja-hallintokorvaus"
                                        :alityyppi nil
                                        :urakka urakka-id
                                        :versio 2
                                        :voimassa_alkaen #inst"2025-10-19T21:00:00.000-00:00"})
        odotettu-historiarivi {:id (inc max-id-ennen-tallennusta)
                               :kulu_kohdistus nil
                               :luoja (:id +kayttaja-jvh+)
                               :luonnos nil
                               :nimi nil
                               :poistettu false
                               :syy "Johtamisen tarve muuttui"
                               :tyyppi "johto-ja-hallintokorvaus"
                               :urakka 36
                               :versio 1
                               :voimassa_alkaen #inst"2025-10-19T21:00:00.000-00:00"}

        historiassa-rivi-updaten-jalkeen-count (ffirst (q (format "SELECT count(*) FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        historiassa-rivi-updaten-jalkeen (first (q-map (format "SELECT id, kulu_kohdistus, luoja, luonnos, nimi, poistettu, syy, tyyppi,
        urakka, versio, voimassa_alkaen FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        ;; validi_aikana on triggeröity special case, joka syytä testata, historiarivi saa ts-rangen loppuarvonsa updatessa
        historiarivi-validi-aikana-alku (ffirst (q (format "SELECT lower(validi_aikana) FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        historiarivi-validi-aikana-loppu (ffirst (q (format "SELECT upper(validi_aikana) FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        ;; alkuperäisen rivin validius
        muutosrivi-validi-aikana-alku (ffirst (q (format "SELECT lower(validi_aikana) FROM ONLY mhu_muutos WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        muutosrivi-validi-aikana-loppu (ffirst (q (format "SELECT upper(validi_aikana) FROM ONLY mhu_muutos WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        ;; voimassaolevan rivin tstzrangen loppu on infinity. Sen testaaminen eksplisiittisesti osoittautui hitaaksi, joten
        ;; tyydytään tässä kohti toteamaan että rivi on voimassa myös esim. vuonna 2035.
        muutosrivi-validi-aikana-loppu-rivi (first (q-map (format "SELECT * FROM ONLY mhu_muutos WHERE validi_aikana @> '2035-07-01 00:00:00.000+00'::timestamp with time zone AND id = %s;" (inc max-id-ennen-tallennusta))))
        ;; tehdään vielä toinen päivitys: tämä varmastaa että historiataulun uniikkius ei rikkoudu triggerin takia
        vastaus-toisen-updaten-jalkeen (filter
                                         #(= "Johtamisen tarve muuttui taas kerran" (:syy %))
                                         (:kirjatut-muutokset
                                           (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-muutos
                                             +kayttaja-jvh+
                                             {:urakka-id urakka-id
                                              :valittu-hoitokausi valittu-hoitokausi
                                              :muutos (assoc muutos-payload
                                                        :id (inc max-id-ennen-tallennusta)
                                                        :syy "Johtamisen tarve muuttui taas kerran")})))
        historirivit-toisen-updaten-jalkeen (q-map (format "SELECT id, versio FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta)))]
    (is (instance? java.util.Date historiarivi-validi-aikana-alku) "onhan pvm")
    (is (instance? java.util.Date historiarivi-validi-aikana-loppu) "onhan pvm")
    (is (instance? java.util.Date muutosrivi-validi-aikana-alku) "onhan pvm")
    (is (= (inc max-id-ennen-tallennusta) (:id muutosrivi-validi-aikana-loppu-rivi)) "oikea rivi palautuu pitkänkin ajan päästä")
    (is (pvm/ennen? historiarivi-validi-aikana-alku historiarivi-validi-aikana-loppu) "validi_aikana on ts-range, jossa alku < loppu")
    (is (pvm/ennen? muutosrivi-validi-aikana-alku muutosrivi-validi-aikana-loppu) "validi_aikana on ts-range, jossa alku < loppu")

    (is (= 1 historiassa-rivi-updaten-jalkeen-count) "historiassa on yksi rivi updaten jälkeen")
    (is (nil? historia-tyhja-insertin-jalkeen) "ei vielä historiatietoa, koska vain INSERT tehtiin")
    (is (= odotettu-historiarivi historiassa-rivi-updaten-jalkeen) "historiatietoa syntyi, koska UPDATE tehtiin")
    ;; assertoidaan luodut, näistä löytyy muokkausmetatiedoista vain luoja ja luotu
    (is (= vastaus-luonnin-jalkeen odotetut-luonnin-jalkeen) "Johto- ja hallintokorvausmuutokset luonnin jälkeen")
    (is (= vastaus-updaten-jalkeen odotetut-updaten-jalkeen) "Johto- ja hallintokorvausmuutokset updaten jälkeen")
    (is (= vastaus-toisen-updaten-jalkeen
          (list (assoc (first odotetut-updaten-jalkeen)
                  :versio 3
                  :syy "Johtamisen tarve muuttui taas kerran")))
      "Johto- ja hallintokorvausmuutokset toisen updaten jälkeen")
    (is (= [{:id (inc max-id-ennen-tallennusta)
             :versio 1}
            {:id (inc max-id-ennen-tallennusta)
             :versio 2}]
          historirivit-toisen-updaten-jalkeen) "historiassa on kaksi riviä toisen updaten jälkeen")))

(deftest hae-yksittaisen-muutoksen-tiedot-lomakkeelle-ii-johto-ja-hallintokorvaus
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        muutos {:id (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos WHERE urakka = " urakka-id " AND syy = 'Työmääräarviot ylittyivät';")),
                :versio 1, :tyyppi "johto-ja-hallintokorvaus"}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-muutoksen-tiedot
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :muutos muutos})
        odotettu-muutostieto {:id (:id muutos)
                              :kulut [{:kulu-id (ffirst (q "SELECT id FROM kulu WHERE lisatieto = 'Muutoksesta automaattisesti luotu kulu 1'"))
                                       :pvm #inst"2025-10-15T00:00:00.000-00:00"
                                       :tavoitehinnan-muutos 1230}]
                              :liitteet nil
                              :tyyppi "johto-ja-hallintokorvaus"
                              :versio 1}]
    (is (= vastaus odotettu-muutostieto) "muutoksen tiedot löytyvät onnistuneesti")))


;; ---


;; -- Pysyvään muutokseen liittyviä testejä --

;; Isoloidumpi testitapaus, josta tehtävien määrien tallennuksen ja haun toimivuuden näkee selvemmin
(deftest tehtavan-maaramuutoksen-tallennus
  ;; Suomussalmen urakassa on helpompi testata useamman hoitovuoden tehtävän määrämuutoksia vuosille 2025 - 2029
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        muutos {:id (ffirst (q "SELECT MAX(id) FROM mhu_muutos WHERE urakka = " urakka-id " AND nimi = 'Päällysteen paikkausmuutos';"))
                :versio 2 :tyyppi "pysyva" :liite-idt #{}}
        poistettava-rivi {:tehtava 24628, :poistettu true :hoitokauden_alkuvuosi 2025}
        ;; Payload muodossa mikä tulisi UI-lomakkeelta osana muuta muutosdataa
        tehtava-maaramuutos-payload [{:tehtava 11235, :uusi? true, :maaramuutos 111, :hoitokauden_alkuvuosi 2025}
                                     {:tehtava 17350, :uusi? true, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}
                                     {:tehtava 6875, :uusi? true, :maaramuutos 333, :hoitokauden_alkuvuosi 2027}
                                     ;; Tehtävät, joilla on negatiivinen ID kuuluu ignorata. Käyttäjä ei ole valinnut
                                     ;; tehtävää käyttöliittymässä.
                                     {:tehtava -1, :uusi? true, :maaramuutos 666, :hoitokauden_alkuvuosi 2027}
                                     {:tehtava -2, :uusi? true, :maaramuutos 666, :hoitokauden_alkuvuosi 2027}
                                     poistettava-rivi
                                     {:tehtava 6953, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}
                                     {:tehtava 6953, :maaramuutos 333, :hoitokauden_alkuvuosi 2027}
                                     ;; Tällä rivillä ei muuteta mitään, jotta nähdään että vanha rivi jää ennalleen, eikä sen versio nouse
                                     ;; Mhu_muutos taulun versio-numero edustaa uusinta versiota, joka on voimassa jollakin joukolla
                                     ;; lapsi-taulujen rivejä. Versioita ei ole tarpeen nostaa turhaan riveille, jotka eivät muutu.
                                     {:tehtava 6953 :maaramuutos 100, :hoitokauden_alkuvuosi 2028}]
        odotettu-vastaus (list
                           {:hoitokauden_alkuvuosi 2027
                            :maaramuutos 333
                            :suunniteltu_maara nil
                            :tehtava 6875
                            :versio 2}
                           {:hoitokauden_alkuvuosi 2026
                            :maaramuutos 222
                            :suunniteltu_maara nil
                            :tehtava 6953
                            :versio 2}
                           {:hoitokauden_alkuvuosi 2027
                            :maaramuutos 333
                            :suunniteltu_maara nil
                            :tehtava 6953
                            :versio 2}
                           {:hoitokauden_alkuvuosi 2028
                            :maaramuutos 100
                            :suunniteltu_maara nil
                            :tehtava 6953
                            :versio 2}
                           {:hoitokauden_alkuvuosi 2025
                            :maaramuutos 111
                            :suunniteltu_maara nil
                            :tehtava 11235
                            :versio 2}
                           {:hoitokauden_alkuvuosi 2026
                            :maaramuutos 100
                            :suunniteltu_maara 0
                            :tehtava 24628
                            :versio 1}
                           {:hoitokauden_alkuvuosi 2027
                            :maaramuutos 100
                            :suunniteltu_maara 0
                            :tehtava 24628
                            :versio 1}
                           {:hoitokauden_alkuvuosi 2028
                            :maaramuutos 100
                            :suunniteltu_maara 0
                            :tehtava 24628
                            :versio 1}
                           {:hoitokauden_alkuvuosi 2026
                            :maaramuutos 222
                            :suunniteltu_maara 0
                            :tehtava 17350
                            :versio 2})

        _ (muutos-palvelu/tallenna-muutoksen-tehtavien-maaramuutokset (:db jarjestelma) (:id +kayttaja-jvh+) urakka-id muutos tehtava-maaramuutos-payload)

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-muutoksen-tiedot
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :muutos muutos})
        poistettu-rivi-historiassa-versio (ffirst (q "SELECT versio FROM mhu_muutos_tehtava_ja_maaraluettelo_historia WHERE muutos = " (:id muutos)
                                                    " AND tehtava = " (:tehtava poistettava-rivi)
                                                    " AND hoitokauden_alkuvuosi = " (:hoitokauden_alkuvuosi poistettava-rivi)
                                                    " ORDER BY hoitokauden_alkuvuosi, tehtava;"))]

    (is (= odotettu-vastaus (flatten (concat (map :tehtavat_ja_maarat (:toimenpiteiden-tiedot vastaus))))))
    ;; Varmistetaan että poistettu rivi löytyy historiasta versiolla 1
    (is (= poistettu-rivi-historiassa-versio 1))))

;; Isoloidumpi testitapaus, josta kustannusvaikutusten tallennuksen ja haun toimivuuden näkee selvemmin
(deftest kustannusvaikutusten-tallennus
  ;; Kustannusvaikutusten testi samaan tapaan kuin tehtävän määrien tallennuksen testi yllä
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        muutos {:id (ffirst (q "SELECT MAX(id) FROM mhu_muutos WHERE urakka = " urakka-id " AND nimi = 'Päällysteen paikkausmuutos';"))
                ;; Bumpataan versiota, jotta nähdään asettuuko odotettujen rivien versio samaan versioon kuin äiti-muutoksen
                :versio 2 :tyyppi "pysyva" :liite-idt #{}}
        ;; Payload muodossa mikä tulisi UI-lomakkeelta osana muuta muutosdataa
        kustannusvaikutus-payload [{:summa 1111, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 125, :hoitokauden_alkuvuosi 2025}
                                   {:summa 2222, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 125, :hoitokauden_alkuvuosi 2026}
                                   {:summa 3333, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 125, :hoitokauden_alkuvuosi 2027}
                                   ;; Tällä rivillä ei muuteta mitään, jotta nähdään että vanha rivi jää ennalleen, eikä sen versio nouse
                                   ;; Mhu_muutos taulun versio-numero edustaa uusinta versiota, joka on voimassa jollakin joukolla
                                   ;; lapsi-taulujen rivejä. Versioita ei ole tarpeen nostaa turhaan riveille, jotka eivät muutu.
                                   {:summa 1000, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 125, :hoitokauden_alkuvuosi 2028}]
        odotettu-vastaus (list
                           {:summa 1111, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 125, :hoitokauden_alkuvuosi 2025, :versio 2}
                           {:summa 2222, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 125, :hoitokauden_alkuvuosi 2026, :versio 2}
                           {:summa 3333, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 125, :hoitokauden_alkuvuosi 2027, :versio 2}
                           ;; Tämän rivin pitäisi jäädä ennalleen alkuperäiseen versioon 1, koska rivi jätettiin tarkoituksella päivittämättä
                           {:summa 1000, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 125, :hoitokauden_alkuvuosi 2028 :versio 1})
        _ (muutos-palvelu/tallenna-muutoksen-kustannusvaikutukset (:db jarjestelma) muutos kustannusvaikutus-payload false)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-muutoksen-tiedot
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :muutos muutos})]

    (is (= odotettu-vastaus (flatten (concat (map :kustannusvaikutukset (:toimenpiteiden-tiedot vastaus))))))))

;; Suomussalmi on urakka, jossa pysyviä muutoksia saadaan useammalle hoitovuodelle 2025-2029
(deftest pysyvan-muutoksen-tallennus-suomussalmi
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        ;; Valittu hoitokausi tulee Muutosten-päänäkymästä, joka näyttää aina vain yhden hoitokauden kerrallaan
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        muutos-syy-insert "Esko tehdä pyöräytti uutta tietä 500 kilometria, täytyy vähän justeerata määriä"
        muutos-payload {:tyyppi "pysyva"
                        :voimassa_alkaen #inst "2025-10-01T10:07:32.000-00:00",
                        :syy muutos-syy-insert,
                        :nimi "Eskon muutos"
                        :tehtavat_ja_maarat [{:tehtava 17345, :uusi? true, :maaramuutos 10, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 11235, :uusi? true, :maaramuutos 111, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 17350, :uusi? true, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}
                                             {:tehtava 6875, :uusi? true, :maaramuutos 333, :hoitokauden_alkuvuosi 2027}
                                             {:tehtava 6953, :maaramuutos 111, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 6953, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}
                                             {:tehtava 6953, :maaramuutos 333, :hoitokauden_alkuvuosi 2027}],
                        :kustannusvaikutukset [{:toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :summa 1111, :hoitokauden_alkuvuosi 2025}
                                               {:toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :summa 2222, :hoitokauden_alkuvuosi 2026}
                                               {:toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :summa 3333, :hoitokauden_alkuvuosi 2027}
                                               {:summa 1111, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 132, :hoitokauden_alkuvuosi 2025}
                                               {:summa 2222, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 132, :hoitokauden_alkuvuosi 2026}
                                               {:summa 333, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 132, :hoitokauden_alkuvuosi 2027}]}
        max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM mhu_muutos;"))
        vastaus-luonnin-jalkeen (filter
                                  #(= muutos-syy-insert (:syy %))
                                  (:kirjatut-muutokset
                                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-muutos
                                      +kayttaja-jvh+
                                      {:urakka-id urakka-id
                                       :valittu-hoitokausi valittu-hoitokausi
                                       :muutos muutos-payload})))
        historia-tyhja-insertin-jalkeen (first (q (format "SELECT * FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        ;; Odotusarvoisesti haetaan valittua hoitokautta vastaavat tulokset
        odotetut-luonnin-jalkeen (list {:id (inc max-id-ennen-tallennusta)
                                        :jjh-muutosten-summa nil
                                        :kulu_kohdistus nil
                                        :kustannusvaikutukset (list
                                                                {:hoitokauden_alkuvuosi 2025
                                                                 :kustannuslaji "hankintakustannukset"
                                                                 :summa 1111
                                                                 :toimenpideinstanssi 129
                                                                 :versio 1}
                                                                {:hoitokauden_alkuvuosi 2025
                                                                 :kustannuslaji "hankintakustannukset"
                                                                 :summa 1111
                                                                 :toimenpideinstanssi 132
                                                                 :versio 1})
                                        :liitteet nil
                                        :luonnos nil
                                        :nimi "Eskon muutos"
                                        :syy "Esko tehdä pyöräytti uutta tietä 500 kilometria, täytyy vähän justeerata määriä"
                                        :tavoitehinnan-muutos 2222
                                        :tehtavat_ja_maarat (list
                                                              {:hoitokauden_alkuvuosi 2025
                                                               :maaramuutos 111
                                                               :suunniteltu_maara nil
                                                               :tehtava 6953
                                                               :versio 1}
                                                              {:hoitokauden_alkuvuosi 2025
                                                               :maaramuutos 111
                                                               :suunniteltu_maara nil
                                                               :tehtava 11235
                                                               :versio 1}
                                                              {:hoitokauden_alkuvuosi 2025
                                                               :maaramuutos 10
                                                               :suunniteltu_maara 0
                                                               :tehtava 17345
                                                               :versio 1})
                                        :tyyppi "pysyva"
                                        :alityyppi nil
                                        :urakka urakka-id
                                        :versio 1
                                        :voimassa_alkaen #inst"2025-09-30T21:00:00.000-00:00"})


        ;; sitten päivitetään samaa muutosta, jolloin tulee rivi historiatietoon...
        muutos-syy-update-1 "Esko teki 100 km lisää tietä, pitääpä justeerata määriä uudestaan"
        muutos-update-payload-1 (assoc muutos-payload
                                  :id (inc max-id-ennen-tallennusta)
                                  :syy muutos-syy-update-1
                                  ;; Päivitetään paria valittua riviä, jotta nähdään että update toimii
                                  ;; Ja, että muiden rivien versiot eivät muutu
                                  :kustannusvaikutukset
                                  (list
                                    {:summa 2, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 132, :hoitokauden_alkuvuosi 2025})
                                  :tehtavat_ja_maarat
                                  (list
                                    {:tehtava 11235, :maaramuutos 2, :hoitokauden_alkuvuosi 2025}))
        vastaus-updaten-jalkeen (filter
                                  #(= muutos-syy-update-1 (:syy %))
                                  (:kirjatut-muutokset
                                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-muutos
                                      +kayttaja-jvh+
                                      {:urakka-id urakka-id
                                       :valittu-hoitokausi valittu-hoitokausi
                                       :muutos muutos-update-payload-1})))
        odotetut-updaten-jalkeen (list {:id (inc max-id-ennen-tallennusta)
                                        :jjh-muutosten-summa nil
                                        :kulu_kohdistus nil
                                        :kustannusvaikutukset (list
                                                                {:hoitokauden_alkuvuosi 2025
                                                                 :kustannuslaji "hankintakustannukset"
                                                                 :summa 1111
                                                                 :toimenpideinstanssi 129
                                                                 :versio 1}
                                                                {:hoitokauden_alkuvuosi 2025
                                                                 :kustannuslaji "hankintakustannukset"
                                                                 :summa 2
                                                                 :toimenpideinstanssi 132
                                                                 :versio 2})
                                        :liitteet nil
                                        :luonnos nil
                                        :nimi "Eskon muutos"
                                        :syy "Esko teki 100 km lisää tietä, pitääpä justeerata määriä uudestaan"
                                        :tavoitehinnan-muutos 1113
                                        :tehtavat_ja_maarat (list
                                                              {:hoitokauden_alkuvuosi 2025
                                                               :maaramuutos 111
                                                               :suunniteltu_maara nil
                                                               :tehtava 6953
                                                               :versio 1}
                                                              {:hoitokauden_alkuvuosi 2025
                                                               :maaramuutos 2
                                                               :suunniteltu_maara nil
                                                               :tehtava 11235
                                                               :versio 2}
                                                              {:hoitokauden_alkuvuosi 2025
                                                               :maaramuutos 10
                                                               :suunniteltu_maara 0
                                                               :tehtava 17345
                                                               :versio 1})
                                        :tyyppi "pysyva"
                                        :alityyppi nil
                                        :urakka 45
                                        :versio 2
                                        :voimassa_alkaen #inst"2025-09-30T21:00:00.000-00:00"})
        odotettu-historiarivi {:id (inc max-id-ennen-tallennusta)
                               :kulu_kohdistus nil
                               :luoja (:id +kayttaja-jvh+)
                               :luonnos nil
                               :nimi "Eskon muutos"
                               :poistettu false
                               :syy muutos-syy-insert
                               :tyyppi "pysyva"
                               :urakka urakka-id
                               :versio 1
                               :voimassa_alkaen #inst"2025-09-30T21:00:00.000-00:00"}

        historiassa-rivi-updaten-jalkeen-count (ffirst (q (format "SELECT count(*) FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        historiassa-rivi-updaten-jalkeen (first (q-map (format "SELECT id, kulu_kohdistus, luoja, luonnos, nimi, poistettu, syy, tyyppi,
        urakka, versio, voimassa_alkaen FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        ;; validi_aikana on triggeröity special case, joka syytä testata, historiarivi saa ts-rangen loppuarvonsa updatessa
        historiarivi-validi-aikana-alku (ffirst (q (format "SELECT lower(validi_aikana) FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        historiarivi-validi-aikana-loppu (ffirst (q (format "SELECT upper(validi_aikana) FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        ;; alkuperäisen rivin validius
        muutosrivi-validi-aikana-alku (ffirst (q (format "SELECT lower(validi_aikana) FROM ONLY mhu_muutos WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        muutosrivi-validi-aikana-loppu (ffirst (q (format "SELECT upper(validi_aikana) FROM ONLY mhu_muutos WHERE id = %s;" (inc max-id-ennen-tallennusta))))
        ;; voimassaolevan rivin tstzrangen loppu on infinity. Sen testaaminen eksplisiittisesti osoittautui hitaaksi, joten
        ;; tyydytään tässä kohti toteamaan että rivi on voimassa myös esim. vuonna 2035.
        muutosrivi-validi-aikana-loppu-rivi (first (q-map (format "SELECT * FROM ONLY mhu_muutos WHERE validi_aikana @> '2035-07-01 00:00:00.000+00'::timestamp with time zone AND id = %s;" (inc max-id-ennen-tallennusta))))

        muutos-syy-update-2 "Esko on kova työmies ja teki taas 50 km lisää tietä, eiköhän tämä ala jo riittää"
        muutos-update-payload-2 (assoc muutos-update-payload-1
                                  ;; Tässä updatessa ei päivitetä muuta kuin syytä-
                                  ;; Lapsitauluihin ei tule muutoksia ja versionostoja
                                  :id (inc max-id-ennen-tallennusta)
                                  :syy muutos-syy-update-2)
        vastaus-toisen-updaten-jalkeen (filter
                                         #(= muutos-syy-update-2 (:syy %))
                                         (:kirjatut-muutokset
                                           (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-muutos
                                             +kayttaja-jvh+
                                             {:urakka-id urakka-id
                                              :valittu-hoitokausi valittu-hoitokausi
                                              :muutos muutos-update-payload-2})))
        historirivit-toisen-updaten-jalkeen (q-map (format "SELECT id, versio FROM mhu_muutos_historia WHERE id = %s;" (inc max-id-ennen-tallennusta)))]

    (is (instance? java.util.Date historiarivi-validi-aikana-alku) "onhan pvm")
    (is (instance? java.util.Date historiarivi-validi-aikana-loppu) "onhan pvm")
    (is (instance? java.util.Date muutosrivi-validi-aikana-alku) "onhan pvm")

    (is (= (inc max-id-ennen-tallennusta) (:id muutosrivi-validi-aikana-loppu-rivi)) "oikea rivi palautuu pitkänkin ajan päästä")
    (is (pvm/ennen? historiarivi-validi-aikana-alku historiarivi-validi-aikana-loppu) "validi_aikana on ts-range, jossa alku < loppu")
    (is (pvm/ennen? muutosrivi-validi-aikana-alku muutosrivi-validi-aikana-loppu) "validi_aikana on ts-range, jossa alku < loppu")

    (is (= vastaus-luonnin-jalkeen odotetut-luonnin-jalkeen) "Pysyvä muutos luonnin jälkeen")
    (is (nil? historia-tyhja-insertin-jalkeen) "Ei vielä historiatietoa, koska vain INSERT tehtiin")

    (is (= vastaus-updaten-jalkeen odotetut-updaten-jalkeen) "Pysyvä muutos updaten jälkeen")
    (is (= 1 historiassa-rivi-updaten-jalkeen-count) "Historiassa on yksi rivi updaten jälkeen")
    (is (= odotettu-historiarivi historiassa-rivi-updaten-jalkeen) "Historiatietoa syntyi, koska UPDATE tehtiin")

    (is (= vastaus-toisen-updaten-jalkeen
          ;; Muutos on sama kuin ensimmäisen updaten jälkeen lapsitaulujen rivien osalta
          ;; Vain syy päivittyy.
          ;; Äitimuutokset mhu_muutos versio nousee kuitenkin versioon 3, kun lapsitaulujen rivit jäävät versioon 2
          ;; TODO: Jos tulee joskus tarve, että pelkkä äitimuutoksen päivitys pitäisi johtaa myös kaikkien lapsitaulujen rivien versioiden nousemiseen,
          ;;       niin vanha logiikka pitää palauttaa ennalleen (SQL upsert-kyselyistä poistetaan DISTINCT FROM)
          ;;       Selvitään myöhemmin voiko tämä aiheuttaa ongelmia historiakyselyissä.
          (into (list)
            (-> (vec odotetut-updaten-jalkeen)
              (assoc-in [0 :syy] muutos-syy-update-2)
              (assoc-in [0 :versio] 3))))
      "Pysyvä muutos toisen updaten jälkeen")
    (is (= [{:id (inc max-id-ennen-tallennusta)
             :versio 1}
            {:id (inc max-id-ennen-tallennusta)
             :versio 2}]
          historirivit-toisen-updaten-jalkeen) "Historiassa on kaksi riviä toisen updaten jälkeen")))


;; -- Lukitun hoitovuoden testit --

(deftest pysyvan-muutoksen-tallennus-kun-hoitovuosi-lukittu-suomussalmi
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        hoitokausi-nro 2

        muutos-payload {:tyyppi "pysyva"
                        ;; Tallennetaan pysyvä muutos voimassa-alkaen vuodelle 2024, ja tarkastellaan lukituksen vaikutusta
                        ;; vain "kokonaisten hoitovuosien" osalta, eli 2025-2026 jne.
                        :voimassa_alkaen #inst "2024-10-01T10:07:32.000-00:00",
                        :syy "Alkuperäinen syy",
                        :nimi "Pysyvä muutos Suomussalmelle"
                        :tehtavat_ja_maarat [{:tehtava 17345, :maaramuutos 10, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 11235, :maaramuutos 111, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 17350, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}
                                             {:tehtava 6875, :maaramuutos 333, :hoitokauden_alkuvuosi 2027}
                                             {:tehtava 6953, :maaramuutos 111, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 6953, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}
                                             {:tehtava 6953, :maaramuutos 333, :hoitokauden_alkuvuosi 2027}],
                        :kustannusvaikutukset [{:summa 1000, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2025}
                                               {:summa 1111, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2025}
                                               {:summa 2222, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2026}
                                               {:summa 2222, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2026}
                                               {:summa 3333, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2027}
                                               {:summa 333, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2027}]}
        max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM mhu_muutos;"))

        ;; Haettava "aiempien hoitovuosien pysyvät muutokset", koska voimassa alkaen on asetettu vuodelle 2024, eli ennen valittua hoitokautta 2025-2026
        vastaus-tallennuksen-jalkeen
        (first (filter #(= "Pysyvä muutos Suomussalmelle" (:nimi %))
                 (:aiempien-hoitovuosien-pysyvat-muutokset
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                     :tallenna-muutos
                     +kayttaja-jvh+
                     {:urakka-id urakka-id
                      :valittu-hoitokausi valittu-hoitokausi
                      :muutos muutos-payload}))))
        odotetut-tallennuksen-jalkeen {:alityyppi nil
                                       :id (inc max-id-ennen-tallennusta)
                                       :jjh-muutosten-summa nil
                                       :kulu_kohdistus nil
                                       :kustannusvaikutukset (list
                                                               {:hoitokauden_alkuvuosi 2025
                                                                :kustannuslaji "hankintakustannukset"
                                                                :summa 1000
                                                                :toimenpideinstanssi 129
                                                                :versio 1}
                                                               {:hoitokauden_alkuvuosi 2025
                                                                :kustannuslaji "hankintakustannukset"
                                                                :summa 1111
                                                                :toimenpideinstanssi 132
                                                                :versio 1})
                                       :liitteet nil
                                       :luonnos nil
                                       :nimi "Pysyvä muutos Suomussalmelle"
                                       :syy "Alkuperäinen syy"
                                       :tavoitehinnan-muutos 2111
                                       :tavoitehinnan-muutos-indeksikorjattu 2345.321
                                       :tehtavat_ja_maarat (list
                                                             {:hoitokauden_alkuvuosi 2025
                                                              :maaramuutos 111
                                                              :suunniteltu_maara nil
                                                              :tehtava 6953
                                                              :versio 1}
                                                             {:hoitokauden_alkuvuosi 2025
                                                              :maaramuutos 111
                                                              :suunniteltu_maara nil
                                                              :tehtava 11235
                                                              :versio 1}
                                                             {:hoitokauden_alkuvuosi 2025
                                                              :maaramuutos 10
                                                              :suunniteltu_maara 0
                                                              :tehtava 17345
                                                              :versio 1})
                                       :tyyppi "pysyva"
                                       :urakka 45
                                       :versio 1
                                       :voimassa_alkaen #inst"2024-09-30T21:00:00.000-00:00"}
        ;; - Nyt lukitaan yksi hoitovuosista vahvistamalla sen tavoitehinta - Hoitovuosi 2025-2026
        ;; Lisätään urakalle sopiva tavoitehinta valmiiksi vahvistettuna - Poistetaan olemassa oleva, jos sellaisia on
        _ (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s;" urakka-id hoitokausi-nro))
        insert-str (format (str
                             "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_indeksikorjattu, "
                             "kattohinta, kattohinta_indeksikorjattu, luotu, indeksikorjaus_vahvistettu) "
                             "VALUES (%s, %s, %s, %s, %s, %s, '2025-10-01T10:00:00.000-00:00', '2025-10-02T10:00:00.000-00:00');")
                     urakka-id hoitokausi-nro 10 10 10 10)
        _ (u insert-str)
        muutos-update-payload-1 {:id (inc max-id-ennen-tallennusta)
                                 :tyyppi "pysyva"
                                 :voimassa_alkaen (:voimassa_alkaen vastaus-tallennuksen-jalkeen),
                                 :syy "Muokkaus 1, yritetään muokata myös lukittua hoitovuotta 2025",
                                 :nimi "Pysyvä muutos Suomussalmelle"
                                 ;; Lukittujen hoitovuosien määrämuutokset ja kustannusvaikutukset pitäisi jättää huomiotta tallentaessa,
                                 ;; kaikkien muiden hoitovuosien osalta ne tallennetaan normaalisti
                                 :tehtavat_ja_maarat [{:tehtava 17345, :maaramuutos 10000, :hoitokauden_alkuvuosi 2025}
                                                      {:tehtava 11235, :maaramuutos 20000, :hoitokauden_alkuvuosi 2025}
                                                      {:tehtava 6953, :maaramuutos 30000, :hoitokauden_alkuvuosi 2025}
                                                      {:tehtava 17350, :maaramuutos 1, :hoitokauden_alkuvuosi 2026}
                                                      {:tehtava 6953, :maaramuutos 1, :hoitokauden_alkuvuosi 2026}
                                                      {:tehtava 6875, :maaramuutos 1, :hoitokauden_alkuvuosi 2027}
                                                      {:tehtava 6953, :maaramuutos 1, :hoitokauden_alkuvuosi 2027}],
                                 :kustannusvaikutukset [{:summa 10000, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2025}
                                                        {:summa 20000, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2025}
                                                        {:summa 1, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2026}
                                                        {:summa 1, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2026}
                                                        {:summa 1, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2027}
                                                        {:summa 1, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2027}]}

        ;; Yritetään päivittää pysyvää muutosta
        ;; Haettava "aiempien hoitovuosien pysyvät muutokset", koska voimassa alkaen on asetettu vuodelle 2024, eli ennen valittua hoitokautta 2025-2026
        vastaus-updaten-jalkeen
        (first (filter #(= "Pysyvä muutos Suomussalmelle" (:nimi %))
                 (:aiempien-hoitovuosien-pysyvat-muutokset
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                     :tallenna-muutos
                     +kayttaja-jvh+
                     {:urakka-id urakka-id
                      :valittu-hoitokausi valittu-hoitokausi
                      :muutos muutos-update-payload-1}))))

        odotetut-updaten-jalkeen {:alityyppi nil
                                  :id 11
                                  :jjh-muutosten-summa nil
                                  :kulu_kohdistus nil
                                  :kustannusvaikutukset (list
                                                          {:hoitokauden_alkuvuosi 2025
                                                           :kustannuslaji "hankintakustannukset"
                                                           :summa 1000
                                                           :toimenpideinstanssi 129
                                                           :versio 1}
                                                          {:hoitokauden_alkuvuosi 2025
                                                           :kustannuslaji "hankintakustannukset"
                                                           :summa 1111
                                                           :toimenpideinstanssi 132
                                                           :versio 1})
                                  :liitteet nil
                                  :luonnos nil
                                  :nimi "Pysyvä muutos Suomussalmelle"
                                  :syy "Muokkaus 1, yritetään muokata myös lukittua hoitovuotta 2025"
                                  :tavoitehinnan-muutos 2111
                                  :tavoitehinnan-muutos-indeksikorjattu 2345.321
                                  :tehtavat_ja_maarat (list
                                                        {:hoitokauden_alkuvuosi 2025
                                                         :maaramuutos 111
                                                         :suunniteltu_maara nil
                                                         :tehtava 6953
                                                         :versio 1}
                                                        {:hoitokauden_alkuvuosi 2025
                                                         :maaramuutos 111
                                                         :suunniteltu_maara nil
                                                         :tehtava 11235
                                                         :versio 1}
                                                        {:hoitokauden_alkuvuosi 2025
                                                         :maaramuutos 10
                                                         :suunniteltu_maara 0
                                                         :tehtava 17345
                                                         :versio 1})
                                  :tyyppi "pysyva"
                                  :urakka 45
                                  :versio 2
                                  :voimassa_alkaen #inst"2024-09-30T21:00:00.000-00:00"}
        vuoden-2026-muutokset (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                       :valittu-hoitokausi [(pvm/->pvm "1.10.2026") (pvm/->pvm "30.09.2027")]})
        ;; Haettava "aiempien hoitovuosien pysyvät muutokset", koska voimassa alkaen on asetettu vuodelle 2024, eli ennen valittua hoitokautta 2026-2027
        vuoden-2026-muutokset (first (filter #(= "Pysyvä muutos Suomussalmelle" (:nimi %))
                                       (:aiempien-hoitovuosien-pysyvat-muutokset
                                         vuoden-2026-muutokset)))
        odotetut-vuodella-2026 {:kustannusvaikutukset (list
                                                        {:hoitokauden_alkuvuosi 2026
                                                         :kustannuslaji "hankintakustannukset"
                                                         :summa 1
                                                         :toimenpideinstanssi 129
                                                         :versio 2}
                                                        {:hoitokauden_alkuvuosi 2026
                                                         :kustannuslaji "hankintakustannukset"
                                                         :summa 1
                                                         :toimenpideinstanssi 132
                                                         :versio 2})
                                :tavoitehinnan-muutos 2
                                :tehtavat_ja_maarat (list
                                                      {:hoitokauden_alkuvuosi 2026
                                                       :maaramuutos 1
                                                       :suunniteltu_maara nil
                                                       :tehtava 6953
                                                       :versio 2}
                                                      {:hoitokauden_alkuvuosi 2026
                                                       :maaramuutos 1
                                                       :suunniteltu_maara 0
                                                       :tehtava 17350
                                                       :versio 2})}]

    (is (= vastaus-tallennuksen-jalkeen odotetut-tallennuksen-jalkeen) "Pysyvä muutos tallennuksen jälkeen")
    (is (= vastaus-updaten-jalkeen odotetut-updaten-jalkeen) "Pysyvä muutos updaten jälkeen, tiedot lukitulla hoitovuodella 2025-2026 eivät saa muuttua")
    ;; Tarkastetaan, että vuoden 2026 tiedot muuttuvat normaalisti, koska hoitovuosi 2026-2027 ei ole lukittu
    (is (= (select-keys vuoden-2026-muutokset [:kustannusvaikutukset :tavoitehinnan-muutos :tehtavat_ja_maarat])
          odotetut-vuodella-2026)
      "Pysyvä muutos haettuna vuodelle 2026-2027, jossa ei ole lukitusta")

    ;; Siivotaan testidatan muutokset
    (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s;" urakka-id hoitokausi-nro))))

;; -- PÄÄTTYY - Lukitun hoitovuoden testit --

;; -- Pysyvän muutoksen voimassa_alkaen -päivämäärän lukituksen testit --
(deftest pysyvan-muutoksen-voimassa-alkaen-kun-hoitovuosi-lukittu-suomussalmi
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        hoitokausi-nro 2

        ;; Lisätään urakalle sopiva tavoitehinta valmiiksi vahvistettuna - Poistetaan olemassa oleva, jos sellaisia on
        _ (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s;" urakka-id hoitokausi-nro))
        insert-str (format (str
                             "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_indeksikorjattu, "
                             "kattohinta, kattohinta_indeksikorjattu, luotu, indeksikorjaus_vahvistettu) "
                             "VALUES (%s, %s, %s, %s, %s, %s, '2025-10-01T10:00:00.000-00:00', '2025-10-02T10:00:00.000-00:00');")
                     urakka-id hoitokausi-nro 10 10 10 10)
        _ (u insert-str)]

    (testing "Uuden pysyvän muutoksen voi tallentaa, vaikka jonkin hoitovuoden indeksikorjaus on vahvistettu"
      (let [muutos-payload {:tyyppi "pysyva"
                            :voimassa_alkaen #inst "2025-10-01T10:07:32.000-00:00",
                            :syy "Pysyvä muutos, jokin hoitovuosista on lukittu",
                            :nimi "Pysyvä muutos Suomussalmelle"
                            :tehtavat_ja_maarat [{:tehtava 17345, :maaramuutos 10, :hoitokauden_alkuvuosi 2025}
                                                 {:tehtava 6875, :maaramuutos 333, :hoitokauden_alkuvuosi 2027}
                                                 {:tehtava 6953, :maaramuutos 111, :hoitokauden_alkuvuosi 2025}
                                                 {:tehtava 6953, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}
                                                 {:tehtava 6953, :maaramuutos 333, :hoitokauden_alkuvuosi 2027}],
                            :kustannusvaikutukset [{:summa 1000, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2025}
                                                   {:summa 1111, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2025}
                                                   {:summa 2222, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2026}
                                                   {:summa 2222, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2026}
                                                   {:summa 3333, :toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2027}
                                                   {:summa 333, :toimenpideinstanssi 132, :kustannuslaji "hankintakustannukset", :hoitokauden_alkuvuosi 2027}]}

            vastaus
            (try
              (first (filter #(= "Pysyvä muutos Suomussalmelle" (:nimi %))
                       (:kirjatut-muutokset
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                           :tallenna-muutos
                           +kayttaja-jvh+
                           {:urakka-id urakka-id
                            :valittu-hoitokausi valittu-hoitokausi
                            :muutos muutos-payload}))))
              (catch Exception e
                (log/error e)
                (ex-data e)))]
        (is (= (:nimi vastaus) "Pysyvä muutos Suomussalmelle")) "Pysyvä muutos tallennettiin onnistuneesti"))

    (testing "Pysyvän muutoksen voimassa_alkaen -päivämäärää ei voi muuttaa, kun jonkin hoitovuoden indeksikorjaus on vahvistettu"
      (let [;; Haetaan testidatassa oleva pysyvä muutos
            muutos-id (ffirst (q "SELECT id FROM mhu_muutos WHERE urakka = " urakka-id " AND tyyppi = 'pysyva' AND nimi = 'Päällysteen paikkausmuutos';"))
            alkuperainen-voimassa-alkaen (ffirst (q "SELECT voimassa_alkaen FROM mhu_muutos WHERE id = " muutos-id ";"))
            uusi-pvm #inst "2025-11-01T10:00:00.000-00:00"
            muutos-uusi-pvm {:id muutos-id
                             :versio 1
                             :tyyppi "pysyva"
                             :nimi "Päällysteen paikkausmuutos"
                             :syy "Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot."
                             :voimassa_alkaen uusi-pvm
                             :kustannusvaikutukset []
                             :tehtavat_ja_maarat []}

            ;; Tallennuksen pitäisi epäonnistua
            virhe (try
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-muutos
                      +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :valittu-hoitokausi valittu-hoitokausi
                       :muutos muutos-uusi-pvm})
                    (catch Exception e
                      (println "Virhedata:" (ex-data e))
                      (ex-data e)))]

        (is (= :harja.palvelin.integraatiot.api.tyokalut.virheet/sisainen-kasittelyvirhe
              (some-> virhe :virheet first :koodi))
          "Tallennus epäonnistui sisäisellä käsittelyvirheellä")))

    (testing "Pysyvän muutoksen muita kenttiä voi muuttaa vaikka jonkin hoitovuoden indeksikorjaus on vahvistettu"
      (let [;; Haetaan testidatassa oleva pysyvä muutos
            muutos-id (ffirst (q "SELECT id FROM mhu_muutos WHERE urakka = " urakka-id " AND tyyppi = 'pysyva' AND nimi = 'Päällysteen paikkausmuutos';"))
            alkuperainen-voimassa-alkaen (ffirst (q "SELECT voimassa_alkaen FROM mhu_muutos WHERE id = " muutos-id ";"))

            muutos {:id muutos-id
                    :versio 1
                    :tyyppi "pysyva"
                    :nimi "Päällysteen paikkausmuutos"
                    :syy "Päivitetty syy testissä"
                    ;; Sama voimassa_alkaen -päivämäärä kuin ennen
                    :voimassa_alkaen alkuperainen-voimassa-alkaen
                    :kustannusvaikutukset []
                    :tehtavat_ja_maarat []}

            ;; Päivitetään muutoksen syytä (ei voimassa_alkaen -päivämäärää)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-muutos
                      +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :valittu-hoitokausi valittu-hoitokausi
                       :muutos muutos})

            paivitetty-muutos (first (filter #(= muutos-id (:id %)) (:kirjatut-muutokset vastaus)))]

        ;; Tarkistetaan että päivitys onnistui
        (is (some? paivitetty-muutos) "Muutos löytyy vastauksesta")
        (is (= (:syy paivitetty-muutos) "Päivitetty syy testissä") "Syy päivittyi")
        (is (= (:voimassa_alkaen paivitetty-muutos) alkuperainen-voimassa-alkaen)
          "voimassa_alkaen -päivämäärä pysyi samana")))

    ;; Siivotaan testidatan muutokset
    (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s;" urakka-id hoitokausi-nro))))

;; -- PÄÄTTYY - Pysyvän muutoksen voimassa_alkaen -päivämäärän lukituksen testit --


;; Suomussalmi on urakka, jossa pysyviä muutoksia saadaan useammalle hoitovuodelle 2025-2029
(deftest hae-yksittaisen-muutoksen-tiedot-lomakkeelle-suomussalmi-pysyva-muutos
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        muutos {:id (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos WHERE urakka = " urakka-id " AND nimi = 'Päällysteen paikkausmuutos';")),
                :versio 1, :tyyppi "pysyva" :liite-idt #{}}
        tpi-id-talvihoito (ffirst (q (format "SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23104') AND %s = urakka;" urakka-id))) ; -- Talvihoito
        tpi-id-liikymp (ffirst (q (format "SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') AND %s = urakka;" urakka-id))) ; -- Liikenneympäristön hoito
        tpi-id-paallpaikk (ffirst (q (format "SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') AND %s = urakka;" urakka-id))) ; -- Päällystepaikkaukset
        tpi-id-soratiet (ffirst (q (format "SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23124') AND %s = urakka;" urakka-id))) ; -- Sorateiden hoito
        tpi-id-mhu-yp (ffirst (q (format "SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') AND %s = urakka;" urakka-id))) ; -- MHU Ylläpito
        tpi-id-korvausinvestointi (ffirst (q (format "SELECT id FROM toimenpideinstanssi WHERE toimenpide = (SELECT id FROM toimenpide WHERE koodi = '14301') AND %s = urakka;" urakka-id))) ; -- MHU Korvausinvestointi
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-muutoksen-tiedot
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :muutos muutos})

        odotettu-muutostieto {:id (:id muutos)
                              :liite-idt #{}
                              :liitteet nil
                              :toimenpiteiden-tiedot [{:budjetoidut_summat (list {:budjetoitu_summa 12000
                                                                                  :hoitokauden_alkuvuosi 2025})
                                                       :id (:id muutos)
                                                       :kustannusvaikutukset (list)
                                                       :tehtavat_ja_maarat (list)
                                                       :toimenpide "Talvihoito"
                                                       :toimenpideinstanssi tpi-id-talvihoito
                                                       :toimenpidekoodi "23104"}
                                                      {:budjetoidut_summat (list {:budjetoitu_summa 9600
                                                                                  :hoitokauden_alkuvuosi 2025})
                                                       :id (:id muutos)
                                                       :kustannusvaikutukset (list)
                                                       :tehtavat_ja_maarat (list)
                                                       :toimenpide "Liikenneympäristön hoito"
                                                       :toimenpideinstanssi tpi-id-liikymp
                                                       :toimenpidekoodi "23116"}
                                                      {:budjetoidut_summat nil
                                                       :id (:id muutos)
                                                       :kustannusvaikutukset (list)
                                                       :tehtavat_ja_maarat (list)
                                                       :toimenpide "Sorateiden hoito"
                                                       :toimenpideinstanssi tpi-id-soratiet
                                                       :toimenpidekoodi "23124"}
                                                      {:budjetoidut_summat (list
                                                                             {:budjetoitu_summa 120000
                                                                              :hoitokauden_alkuvuosi 2025}
                                                                             {:budjetoitu_summa 120000
                                                                              :hoitokauden_alkuvuosi 2026})
                                                       :id (:id muutos)
                                                       :kustannusvaikutukset (list
                                                                               {:hoitokauden_alkuvuosi 2025
                                                                                :kustannuslaji "hankintakustannukset"
                                                                                :summa 1000
                                                                                :toimenpideinstanssi 125
                                                                                :versio 1}
                                                                               {:hoitokauden_alkuvuosi 2026
                                                                                :kustannuslaji "hankintakustannukset"
                                                                                :summa 1000
                                                                                :toimenpideinstanssi 125
                                                                                :versio 1}
                                                                               {:hoitokauden_alkuvuosi 2027
                                                                                :kustannuslaji "hankintakustannukset"
                                                                                :summa 1000
                                                                                :toimenpideinstanssi 125
                                                                                :versio 1}
                                                                               {:hoitokauden_alkuvuosi 2028
                                                                                :kustannuslaji "hankintakustannukset"
                                                                                :summa 1000
                                                                                :toimenpideinstanssi 125
                                                                                :versio 1})
                                                       :tehtavat_ja_maarat (list
                                                                             {:hoitokauden_alkuvuosi 2025
                                                                              :maaramuutos 100
                                                                              :suunniteltu_maara 0
                                                                              :tehtava 24628
                                                                              :versio 1}
                                                                             {:hoitokauden_alkuvuosi 2026
                                                                              :maaramuutos 100
                                                                              :suunniteltu_maara 0
                                                                              :tehtava 24628
                                                                              :versio 1}
                                                                             {:hoitokauden_alkuvuosi 2027
                                                                              :maaramuutos 100
                                                                              :suunniteltu_maara 0
                                                                              :tehtava 24628
                                                                              :versio 1}
                                                                             {:hoitokauden_alkuvuosi 2028
                                                                              :maaramuutos 100
                                                                              :suunniteltu_maara 0
                                                                              :tehtava 24628
                                                                              :versio 1})
                                                       :toimenpide "Päällysteiden paikkaus"
                                                       :toimenpideinstanssi tpi-id-paallpaikk
                                                       :toimenpidekoodi "20107"}
                                                      {:budjetoidut_summat nil
                                                       :id (:id muutos)
                                                       :kustannusvaikutukset (list)
                                                       :tehtavat_ja_maarat (list)
                                                       :toimenpide "MHU Ylläpito"
                                                       :toimenpideinstanssi tpi-id-mhu-yp
                                                       :toimenpidekoodi "20191"}
                                                      {:budjetoidut_summat nil
                                                       :id (:id muutos)
                                                       :kustannusvaikutukset (list)
                                                       :tehtavat_ja_maarat (list)
                                                       :toimenpide "MHU Korvausinvestointi"
                                                       :toimenpideinstanssi tpi-id-korvausinvestointi
                                                       :toimenpidekoodi "14301"}]
                              :tyyppi "pysyva"
                              :versio 1}]
    ;; toimenpiteiden tehtävät on pitkälistaus, tässä kohti ei ole ainakaan mielekästi dumpata odotettua tulosta
    ;; käytämme niiden hakemiseen valmista palvelua johon on jo omat testinsä.
    (is (= (dissoc vastaus :toimenpiteiden-tehtavat) odotettu-muutostieto) "muutoksen tiedot löytyvät onnistuneesti")))

(deftest johto-ja-hallintokorvausmuutoksen-kulu-2025-ja-jalkeen-oltava-negatiivinen
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        muutos-payload {:voimassa_alkaen #inst "2025-06-25T10:07:32.000-00:00",
                        :syy "Johtamisen tarve muuttui",
                        :kulut (list {:pvm #inst "2025-10-14T21:00:00.000-00:00", :tavoitehinnan-muutos 10})
                        :tyyppi "johto-ja-hallintokorvaus"}]
    (is (thrown? Error
          (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-muutos
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :valittu-hoitokausi valittu-hoitokausi
             :muutos muutos-payload}))
      "Johto- ja hallintokorvausmuutoksen kulu 2025 ja jälkeen on oltava negatiivinen")))

(defn- muutospayload-liitteilla [muutos-id urakka-id liitteet]
  {:kulu_kohdistus nil,
   :kustannusvaikutukset (list
                           {:summa 1000, :toimenpide 700, :kustannuslaji "hankintakustannukset" :hoitokauden_alkuvuosi 2025}),
   :voimassa_alkaen #inst"2025-09-30T21:00:00.000-00:00",
   :syy "Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.",
   :tehtavat_ja_maarat (list
                         {:tehtava 17341, :maaramuutos -40, :hoitokauden_alkuvuosi 2025}
                         {:tehtava 17346, :maaramuutos -30, :hoitokauden_alkuvuosi 2025})
   :urakka urakka-id,
   :nimi "Tämän hoitovuoden määräpoikkeamamuutos",
   :id muutos-id,
   :jjh-muutosten-summa nil,
   :liitteet liitteet,
   :luonnos false,
   :kulut nil,
   :tavoitehinnan-muutos 1000,
   :tyyppi "muutostyo"
   :alityyppi :poikkeama})

(deftest testaa-muutoksen-liitteiden-lisays-ja-poisto
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        muutos-id (ffirst (q "SELECT id FROM mhu_muutos WHERE urakka = " urakka-id " AND syy = 'Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.';"))
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        tiedosto "dev-resources/images/harja-brand-text.png"
        tiedoston-sisalto (IOUtils/toByteArray (io/input-stream tiedosto))
        olemassaoleva-liite-id (ffirst (q "SELECT id FROM liite WHERE nimi = 'rumpu.jpg'"))
        luotu-liite (liitteet-komponentti/luo-liite liitteiden-hallinta nil (hae-aktiivinen-oulu-testi-id) "harja-brand-text.png" "image/png" 3 tiedoston-sisalto nil "harja-ui")
        luotu-liite-id (:id luotu-liite)
        ;; muutoksessa on testidatan kautta ennestään jo yksi liite, lisätään yksi uusi liite
        liitteet (list
                   {:id luotu-liite-id :kuvaus nil, :virustarkastettu? true, :urakka urakka-id, :nimi "harja-brand-text.png", :s3hash nil, :lahde "harja-ui", :tyyppi "image/png", :koko 2507}
                   {:id olemassaoleva-liite-id, :nimi "rumpu.jpg", :kuvaus nil, :tyyppi "image/png", :koko nil, :liite_oid nil, :virustarkastettu? true})
        liitelinkkien-maara-ennen-tallennusta (ffirst (q (format "SELECT COUNT(*) FROM mhu_muutos_liite WHERE muutos = %s;" muutos-id)))
        muutos-payload (muutospayload-liitteilla muutos-id urakka-id liitteet)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-muutos
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :valittu-hoitokausi valittu-hoitokausi
                   :muutos muutos-payload})
        liitelinkkien-maara-tallennuksen-jalkeen (ffirst (q (format "SELECT COUNT(*) FROM mhu_muutos_liite WHERE muutos = %s;" muutos-id)))
        kirjatut (:kirjatut-muutokset vastaus)
        paivitetty (first (filter #(= (:id %) muutos-id) kirjatut))
        odotetut-liite-linkit (list {:id olemassaoleva-liite-id, :muutos muutos-id} {:id luotu-liite-id, :muutos muutos-id})
        liitteet-poistava-payload (muutospayload-liitteilla muutos-id urakka-id [])
        liitteet-poistettu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :tallenna-muutos
                                     +kayttaja-jvh+
                                     {:urakka-id urakka-id
                                      :valittu-hoitokausi valittu-hoitokausi
                                      :muutos liitteet-poistava-payload})
        paivitetty-liitteet-poistettu (first (filter #(= (:id %) muutos-id) (:kirjatut-muutokset liitteet-poistettu-vastaus)))
        liitelinkkien-maara-liitteiden-poiston-jalkeen (ffirst (q (format "SELECT COUNT(*) FROM mhu_muutos_liite WHERE muutos = %s;" muutos-id)))]
    (is (= 1 liitelinkkien-maara-ennen-tallennusta) "Ennen tallennusta oli yksi liitelinkki muutoksessa")
    (is (= 2 liitelinkkien-maara-tallennuksen-jalkeen) "Tallennuksen jälkeen kaksi liitelinkkiä")
    (is (= odotetut-liite-linkit (:liitteet paivitetty)) "Liitteiden linkit on päivitetty muutokseen")
    (is (= 0 liitelinkkien-maara-liitteiden-poiston-jalkeen) "Liitteiden poistamisen jälkeen ei liitelinkkejä")
    (is (nil? (:liitteet paivitetty-liitteet-poistettu)) "Ei palaudu enää liitteitä kun ne on poistettu muutoksesta.")))

;; --- Muutostyöt ----

(deftest hae-urakan-muutostyot-toimii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2021 2026))

        params {:urakka-id urakka-id
                :valittu-hoitokausi (last hoitokaudet)}

        vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :hae-urakan-muutostyot +kayttaja-jvh+ params)]

    (is (= (count vastaus) 2) "Urakalla olemassa 2 muutostyötä")

    (is (some #(= "Erillisrahoitettu sorastusmuutos" (:nimi %)) vastaus)
      "Muutostyö löytyy")

    (is (some #(= "Tämän hoitovuoden määräpoikkeamamuutos" (:nimi %)) vastaus)
      "Muutostyö löytyy")

    (is (every? #(= #inst "2025-09-30T21:00:00.000-00:00" (:voimassa_alkaen %)) vastaus)
      "Molemmilla sama voimassa_alkaen")))

(deftest muutostyo-erillisrahoitettu-tallennus-suomussalmi
  (testing "Tallennataan erillisrahoitettu muutostyö, joka kohdistuu valittuun hoitokauteen"
    (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
          valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
          muutos-nimi-insert "Erillisrahoitettu1"
          muutos-payload {:nimi muutos-nimi-insert
                          :syy "Erillisrahoitettu testimuutos",
                          :voimassa_alkaen #inst "2025-10-25T10:07:32.000-00:00",
                          :tavoitehinnan-muutos 5000,
                          :tyyppi "muutostyo"
                          :alityyppi :erillisrahoitus}
          max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
          muutos-luonnin-jalkeen (first (filter #(= muutos-nimi-insert (:nimi %))
                                          (:kirjatut-muutokset (kutsu-palvelua (:http-palvelin jarjestelma)
                                                                 :tallenna-muutos
                                                                 +kayttaja-jvh+
                                                                 {:urakka-id urakka-id
                                                                  :valittu-hoitokausi valittu-hoitokausi
                                                                  :muutos muutos-payload}))))
          odotettu-luonnin-jalkeen {:id 11
                                    :versio 1
                                    :voimassa_alkaen #inst"2025-10-24T21:00:00.000-00:00"
                                    :urakka 45
                                    :tyyppi "muutostyo"
                                    :alityyppi :erillisrahoitus
                                    :jjh-muutosten-summa nil
                                    :kulu_kohdistus nil
                                    :kustannusvaikutukset (list {:hoitokauden_alkuvuosi 2025
                                                                 :kustannuslaji "erillishankinnat"
                                                                 :summa 5000
                                                                 :toimenpideinstanssi nil
                                                                 :versio 1})
                                    :liitteet nil
                                    :luonnos nil
                                    :nimi "Erillisrahoitettu1"
                                    :syy "Erillisrahoitettu testimuutos"
                                    :tavoitehinnan-muutos 5000
                                    :tehtavat_ja_maarat ()}
          ;; Sitten päivitetään samaa muutosta, jolloin tulee rivi historiatietoon...
          muutos-syy-update-1 "Erillisrahoitettu testimuutos päivitetty 1"
          muutos-update-payload-1 (assoc muutos-payload
                                    :id (inc max-id-ennen-tallennusta)
                                    :syy muutos-syy-update-1
                                    :tavoitehinnan-muutos 6000)
          muutos-updaten-jalkeen (first (filter
                                          #(= muutos-syy-update-1 (:syy %))
                                          (:kirjatut-muutokset
                                            (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :tallenna-muutos
                                              +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :valittu-hoitokausi valittu-hoitokausi
                                               :muutos muutos-update-payload-1}))))
          odotettu-updaten-jalkeen {:id (inc max-id-ennen-tallennusta)
                                    :versio 2
                                    :voimassa_alkaen #inst"2025-10-24T21:00:00.000-00:00"
                                    :urakka 45
                                    :nimi "Erillisrahoitettu1"
                                    :syy muutos-syy-update-1
                                    :tyyppi "muutostyo"
                                    :alityyppi :erillisrahoitus
                                    :jjh-muutosten-summa nil
                                    :kulu_kohdistus nil
                                    :kustannusvaikutukset (list {:hoitokauden_alkuvuosi 2025
                                                                 :kustannuslaji "erillishankinnat"
                                                                 :summa 6000
                                                                 :toimenpideinstanssi nil
                                                                 :versio 2})
                                    :liitteet nil
                                    :luonnos nil
                                    :tavoitehinnan-muutos 6000
                                    :tehtavat_ja_maarat ()}]

      (is (= odotettu-luonnin-jalkeen muutos-luonnin-jalkeen) "Muutostiedot luonnin jälkeen")
      (is (= odotettu-updaten-jalkeen muutos-updaten-jalkeen) "Muutostiedot updaten jälkeen")))

  (testing "Muutostyö ei kohdistu valittuun hoitokauteen"
    (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
          valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
          muutos-nimi-insert "Erillisrahoitettu1"
          muutos-payload {:nimi muutos-nimi-insert
                          :syy "Erillisrahoitettu testimuutos",
                          :voimassa_alkaen #inst "2025-06-25T10:07:32.000-00:00",
                          :tavoitehinnan-muutos 5000,
                          :tyyppi "muutostyo"
                          :alityyppi :erillisrahoitus}]

      ;; Heitetään virhe, jos yritetään tallentaa muutostyötä, joka ei kohdistu valittuun hoitokauteen
      (is (thrown? Exception
            (kutsu-palvelua (:http-palvelin jarjestelma)
              :tallenna-muutos
              +kayttaja-jvh+
              {:urakka-id urakka-id
               :valittu-hoitokausi valittu-hoitokausi
               :muutos muutos-payload}))))))

;; ----

;; -- Kirjatun muutoksen poistaminen (pysyvä muutos, muutostyö, johto- ja hallintokorvauksen muutos) ----

(defn poista-muutos [kayttaja tiedot]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :poista-muutos
    kayttaja
    tiedot))

(deftest poista-pysyva-muutos-suomussalmi
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2021 2026))
        max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
        ;; Luodaan ensin uusi pysyvä muutos
        muutos-payload {:tyyppi "pysyva"
                        :voimassa_alkaen #inst "2025-10-02T10:07:32.000-00:00"
                        :syy "Testattava pysyvä muutos"
                        :nimi "Poistettava muutos"
                        :tehtavat_ja_maarat [{:tehtava 24628, :maaramuutos 50, :hoitokauden_alkuvuosi 2025}]
                        :kustannusvaikutukset [{:summa 500
                                                :toimenpideinstanssi 90
                                                :kustannuslaji "hankintakustannukset"
                                                :hoitokauden_alkuvuosi 2025}]}
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-muutos
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :valittu-hoitokausi valittu-hoitokausi
             :muutos muutos-payload})
        muutos-id (inc max-id-ennen-tallennusta)
        ;; Varmistetaan että muutos on olemassa
        muutos-ennen-poistoa (first (q (format "SELECT * FROM mhu_muutos WHERE id = %s AND poistettu = false;" muutos-id)))

        ;; Poistetaan muutos
        vastaus-poiston-jalkeen (poista-muutos +kayttaja-jvh+
                                  {:muutos-id muutos-id
                                   :urakka-id urakka-id
                                   :valittu-hoitokausi valittu-hoitokausi
                                   :hoitokaudet hoitokaudet
                                   :laskenta-automatiikka? true})

        ;; Varmistetaan että muutos on merkitty poistetuksi
        muutos-poiston-jalkeen (first (q (format "SELECT poistettu FROM mhu_muutos WHERE id = %s;" muutos-id)))
        historia-poiston-jalkeen (q-map (format "SELECT poistettu, versio FROM mhu_muutos_historia WHERE id = %s" muutos-id))
        poistettu-muutoksia-vastauksessa (filter #(= muutos-id (:id %))
                                           (:kirjatut-muutokset vastaus-poiston-jalkeen))]

    (is (not (nil? muutos-ennen-poistoa)) "Muutos on olemassa ennen poistoa")
    (is (true? (first muutos-poiston-jalkeen)) "Muutos on merkitty poistetuksi tietokannassa")
    (is (= [{:poistettu false :versio 1}] historia-poiston-jalkeen) "Vanha versio historiataulussa on oikein")
    (is (empty? poistettu-muutoksia-vastauksessa) "Poistettu muutos ei palaudu haussa")))

(deftest poista-pysyva-muutos-kun-hoitovuosi-lukittu-suomussalmi
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2024 2029))
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        lukittava-hoitokausi-nro 3
        max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
        ;; Luodaan ensin uusi pysyvä muutos
        muutos-payload {:tyyppi "pysyva"
                        :voimassa_alkaen #inst "2025-10-01T10:07:32.000-00:00"
                        :syy "Testattava pysyvä muutos vahvistetulle hoitovuodelle"
                        :nimi "Poistolle tarkoitettu muutos vahvistettu"
                        :tehtavat_ja_maarat [{:tehtava 1448, :maaramuutos 50, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 1448, :maaramuutos 50, :hoitokauden_alkuvuosi 2026}]
                        ;; Hox: Toimenpideinstanssit ovat urakkakohtaisia.
                        :kustannusvaikutukset [{:toimenpideinstanssi 122, :kustannuslaji "hankintakustannukset", :summa 100, :hoitokauden_alkuvuosi 2025}
                                               {:toimenpideinstanssi 122, :kustannuslaji "hankintakustannukset", :summa 100, :hoitokauden_alkuvuosi 2026}]}

        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-muutos
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :valittu-hoitokausi valittu-hoitokausi
             :muutos muutos-payload})
        muutos-id (inc max-id-ennen-tallennusta)

        ;; Vahvistetaan hoitovuosi 2025-2026 (hoitokausi 2)
        _ (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s;" urakka-id lukittava-hoitokausi-nro))
        insert-str (format (str
                             "INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_indeksikorjattu, "
                             "kattohinta, kattohinta_indeksikorjattu, luotu, indeksikorjaus_vahvistettu) "
                             "VALUES (%s, %s, %s, %s, %s, %s, '2025-10-01T10:00:00.000-00:00', '2025-10-02T10:00:00.000-00:00');")
                     urakka-id lukittava-hoitokausi-nro 10 10 10 10)
        _ (u insert-str)
        poisto-epaonnistui? (atom false)]

    (try
      (poista-muutos +kayttaja-jvh+
        {:muutos-id muutos-id
         :urakka-id urakka-id
         :valittu-hoitokausi valittu-hoitokausi
         :hoitokaudet hoitokaudet
         :laskenta-automatiikka? true})
      (catch Exception e
        (println "Virhedata:" (ex-data e))
        (reset! poisto-epaonnistui? true)))

    (is (true? @poisto-epaonnistui?) "Pysyvän muutoksen poisto vahvistetulla hoitovuodella epäonnistuu")

    ;; Siivotaan testidatan muutokset
    (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s;" urakka-id lukittava-hoitokausi-nro))))

(deftest poista-johto-ja-hallintokorvaus-muutos
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2024 2029))
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]

        ;; Luo JJH-muutos
        max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
        muutos-payload {:tyyppi "johto-ja-hallintokorvaus"
                        :voimassa_alkaen #inst "2025-10-20T10:07:32.000-00:00",
                        :syy "Johtamisen tarve muuttui",
                        :kulut (list
                                 {:pvm #inst "2025-10-14T21:00:00.000-00:00", :tavoitehinnan-muutos 10}
                                 {:pvm #inst "2025-11-14T22:00:00.000-00:00", :tavoitehinnan-muutos 20}
                                 {:pvm #inst "2025-12-14T22:00:00.000-00:00", :tavoitehinnan-muutos 30}
                                 {:pvm #inst "2026-01-14T22:00:00.000-00:00", :tavoitehinnan-muutos 40}
                                 {:pvm #inst "2026-02-14T22:00:00.000-00:00", :tavoitehinnan-muutos 50}
                                 {:pvm #inst "2026-03-14T22:00:00.000-00:00", :tavoitehinnan-muutos 60}
                                 {:pvm #inst "2026-04-14T21:00:00.000-00:00", :tavoitehinnan-muutos 70}
                                 {:pvm #inst "2026-05-14T21:00:00.000-00:00", :tavoitehinnan-muutos 80}
                                 {:pvm #inst "2026-06-14T21:00:00.000-00:00", :tavoitehinnan-muutos 90}
                                 {:pvm #inst "2026-07-14T21:00:00.000-00:00", :tavoitehinnan-muutos 100}
                                 {:pvm #inst "2026-08-14T21:00:00.000-00:00", :tavoitehinnan-muutos 110}
                                 {:pvm #inst "2026-09-14T21:00:00.000-00:00", :tavoitehinnan-muutos 120})}
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-muutos
            +kayttaja-jvh+
            {:urakka-id urakka-id
             :valittu-hoitokausi valittu-hoitokausi
             :muutos muutos-payload})
        muutos-id (inc max-id-ennen-tallennusta)
        ;; Hae luodut kulut
        kulut-ennen (q (str "SELECT k.id FROM kulu k"
                         " JOIN mhu_muutos_kulu mmk ON k.id = mmk.kulu"
                         " WHERE k.poistettu IS NOT TRUE"
                         " AND lisatieto = 'Muutoksesta automaattisesti luotu kulu'"
                         " AND mmk.muutos = " muutos-id))
        kohdistukset-ennen (q (str "SELECT kk.id FROM kulu_kohdistus kk"
                                " JOIN kulu k ON kk.kulu = k.id"
                                " JOIN mhu_muutos_kulu mmk ON k.id = mmk.kulu"
                                " WHERE k.poistettu IS NOT TRUE"
                                " AND kk.tyyppi = 'jjh-muutos'"
                                " AND kk.poistettu IS NOT TRUE"
                                " AND mmk.muutos = " muutos-id))
        linkitykset-ennen (q (str "SELECT * FROM mhu_muutos_kulu WHERE muutos = " muutos-id))]

    ;; Varmista että kulut, kohdistukset ja linkitykset on luotu oikein
    (is (= 12 (count kulut-ennen)) "JJH-muutokselle luotiin yksi kulu")
    (is (= 12 (count kohdistukset-ennen)) "Kuluille luotiin kohdistus")
    (is (= 12 (count linkitykset-ennen)) "Muutos linkitettiin kuluun")

    ;; Varmista että muutos on poistettu
    (let [;; Poista muutos
          vastaus-poiston-jalkeen (poista-muutos +kayttaja-jvh+
                                    {:muutos-id muutos-id
                                     :urakka-id urakka-id
                                     :valittu-hoitokausi valittu-hoitokausi
                                     :hoitokaudet hoitokaudet
                                     :laskenta-automatiikka? true})
          muutos-poiston-jalkeen (first (q (format "SELECT poistettu FROM mhu_muutos WHERE id = %s;" muutos-id)))
          historia-poiston-jalkeen (q-map (format "SELECT poistettu, versio FROM mhu_muutos_historia WHERE id = %s" muutos-id))
          poistettu-muutoksia-vastauksessa (filter #(= muutos-id (:id %))
                                             (:kirjatut-muutokset vastaus-poiston-jalkeen))
          kulut-jalkeen (q (str "SELECT k.id FROM kulu k"
                             " JOIN mhu_muutos_kulu mmk ON k.id = mmk.kulu"
                             " WHERE k.poistettu IS NOT TRUE"
                             " AND lisatieto = 'Muutoksesta automaattisesti luotu kulu'"
                             " AND mmk.muutos = " muutos-id))
          kohdistukset-jalkeen (q (str "SELECT kk.id FROM kulu_kohdistus kk"
                                    " JOIN kulu k ON kk.kulu = k.id"
                                    " JOIN mhu_muutos_kulu mmk ON k.id = mmk.kulu"
                                    " WHERE k.poistettu IS NOT TRUE"
                                    " AND kk.tyyppi = 'jjh-muutos'"
                                    " AND kk.poistettu IS NOT TRUE"
                                    " AND mmk.muutos = " muutos-id))
          linkitykset-jalkeen (q (str "SELECT * FROM mhu_muutos_kulu WHERE muutos = " muutos-id))]

      (is (true? (first muutos-poiston-jalkeen)) "Muutos on merkitty poistetuksi tietokannassa")
      (is (= [{:poistettu false :versio 1}] historia-poiston-jalkeen) "Vanha versio historiataulussa on oikein")
      (is (empty? poistettu-muutoksia-vastauksessa) "Poistettu muutos ei palaudu haussa")
      (is (= 0 (count kulut-jalkeen)) "Kulut on poistettu")
      (is (= 0 (count kohdistukset-jalkeen)) "Kohdistukset on poistettu")
      ;; Muutoksen lapsitaulut pysyvät ennallaan, tieto poistosta löytyy MHU_muutos.poistettu -sarakkeesta
      (is (= 12 (count linkitykset-jalkeen)) "MHU-muutos linkitykset säilyvät tietokannassa ennallaan"))))

(deftest poista-erillisrahoitettu-muutostyo-suomussalmi
  (testing "Muutoksen poistaminen onnistuu, jos muutokseen EI OLE kohdistettu kuluja"
    (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
          hoitokaudet (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                        (range 2024 2029))
          valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]

          ;; Luo erillisrahoitettu muutostyö
          max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
          muutos-payload {:tyyppi "muutostyo"
                          :alityyppi :erillisrahoitus
                          :voimassa_alkaen #inst "2025-10-20T10:07:32.000-00:00"
                          :syy "Erillisrahoitettu testimuutos poistoa varten"
                          :nimi "Poistettava erillisrahoitettu muutos"
                          :tavoitehinnan-muutos 5250
                          :kustannusvaikutukset [{:hoitokauden_alkuvuosi 2025
                                                  :kustannuslaji "erillishankinnat"
                                                  :summa 5250
                                                  :toimenpideinstanssi nil}]}
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
              :tallenna-muutos
              +kayttaja-jvh+
              {:urakka-id urakka-id
               :valittu-hoitokausi valittu-hoitokausi
               :muutos muutos-payload})
          muutos-id (inc max-id-ennen-tallennusta)

          ;; Varmista että muutos luotiin
          muutos-ennen (ffirst (q (format "SELECT poistettu FROM mhu_muutos WHERE id = %s;" muutos-id)))

          ;; Tarkista että muutokselle ei ole kuluja
          kulut-ennen (ffirst (q (str
                                   "SELECT COUNT(*) AS maara FROM kulu_kohdistus kk"
                                   " WHERE kk.muutos = " muutos-id
                                   " AND kk.poistettu IS NOT TRUE")))]

      (is (false? muutos-ennen) "Muutos on luotu eikä poistettu")
      (is (= 0 kulut-ennen) "Muutokselle ei ole kohdistettu kuluja")

      ;; Poista muutos
      (let [vastaus-poiston-jalkeen (poista-muutos +kayttaja-jvh+
                                      {:muutos-id muutos-id
                                       :urakka-id urakka-id
                                       :valittu-hoitokausi valittu-hoitokausi
                                       :hoitokaudet hoitokaudet
                                       :laskenta-automatiikka? true})
            muutos-poiston-jalkeen (ffirst (q (format "SELECT poistettu FROM mhu_muutos WHERE id = %s;" muutos-id)))
            poistettu-muutoksia-vastauksessa (filter #(= muutos-id (:id %))
                                               (:kirjatut-muutokset vastaus-poiston-jalkeen))]

        (is (true? muutos-poiston-jalkeen) "Muutos on merkitty poistetuksi tietokannassa")
        (is (empty? poistettu-muutoksia-vastauksessa) "Poistettu muutos ei palaudu haussa"))))

  (testing "Muutostyön poistaminen ei onnistu, jos muutokseen ON kohdistettu kuluja"
    (let [urakka-id (hae-urakan-id-nimella "POP MHU Suomussalmi 2024-2029")
          tpi-id (hae-toimenpideinstanssi-id-nimella "POP MHU Suomussalmi 2024-2029 Talvihoito TP")
          hoitokaudet (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                        (range 2024 2029))
          valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]

          ;; Luo erillisrahoitettu muutostyö
          max-id-ennen-tallennusta (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos;"))
          muutos-payload {:tyyppi "muutostyo"
                          :alityyppi :erillisrahoitus
                          :voimassa_alkaen #inst "2025-10-20T10:07:32.000-00:00"
                          :syy "Erillisrahoitettu testimuutos jolla on kuluja"
                          :nimi "Muutos jolla kuluja"
                          :tavoitehinnan-muutos 5000
                          :kustannusvaikutukset [{:hoitokauden_alkuvuosi 2025
                                                  :kustannuslaji "erillishankinnat"
                                                  :summa 5000
                                                  :toimenpideinstanssi nil}]}
          muutostiedot (kutsu-palvelua (:http-palvelin jarjestelma)
                         :tallenna-muutos
                         +kayttaja-jvh+
                         {:urakka-id urakka-id
                          :valittu-hoitokausi valittu-hoitokausi
                          :muutos muutos-payload})
          muutos-id (inc max-id-ennen-tallennusta)
          luotu-muutos (first (filter #(= muutos-id (:id %)) (:kirjatut-muutokset muutostiedot)))

          ;; Tallenna kulu muutokselle
          kulu {:kokonaissumma 250
                :erapaiva (pvm/->pvm "25.10.2025")
                :kohdistukset [{:tyyppi :erillisrahoitettu-muutos
                                :valittu-muutostyo (assoc luotu-muutos :budjetoitu_summa 5000)
                                :toimenpideinstanssi tpi-id
                                :rivi 0
                                :summa 250
                                :tavoitehintainen :true
                                :lukittu? false
                                :lisatyo? false
                                :poistettu false}]
                :urakka urakka-id
                :liitteet []
                :tyyppi "laskutettava"
                :koontilaskun-kuukausi "lokakuu/2-hoitovuosi"}

          kulu-vastaus (kutsu-palvelua
                         (:http-palvelin jarjestelma)
                         :tallenna-kulu +kayttaja-jvh+
                         {:urakka-id urakka-id
                          :kulu-kohdistuksineen kulu})

          ;; Varmista että kulu on kohdistettu
          kulut-ennen (q (str "SELECT COUNT(*) AS maara FROM kulu_kohdistus kk"
                           " WHERE kk.muutos = " muutos-id
                           " AND kk.poistettu IS NOT TRUE"))
          kulut-ennen-2 (muutos-kyselyt/hae-muutostyon-kulujen-maara (:db jarjestelma) {:muutos-id muutos-id})

          poisto-epaonnistui? (atom false)
          virheviesti (atom nil)]

      (is (= 250M (:kokonaissumma kulu-vastaus)) "Kulu tallentui kantaan")
      (is (= 1 (ffirst kulut-ennen)) "Muutokselle on kohdistettu kuluja")
      (is (= 1 kulut-ennen-2) "Muutokselle on kohdistettu kuluja (kyselyt namespace)")

      ;; Yritä poistaa muutos - pitäisi epäonnistua, koska muutokselle on kohdistettu kuluja
      (try
        (poista-muutos +kayttaja-jvh+
          {:muutos-id muutos-id
           :urakka-id urakka-id
           :valittu-hoitokausi valittu-hoitokausi
           :hoitokaudet hoitokaudet
           :laskenta-automatiikka? true})
        (catch Exception e
          (let [data (ex-data e)]
            (reset! poisto-epaonnistui? true)
            (reset! virheviesti (some-> data :virheet first :viesti)))))

      (is (true? @poisto-epaonnistui?) "Muutoksen poisto kulujen kanssa epäonnistuu")
      (is (= "Muutostyölle on kohdistettu kuluja. Poista kohdistetut kulut ennen muutoksen poistamista."
            @virheviesti)
        "Virheviesti on oikea"))))


;; ----




;; -- Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset ----

(defn- hae-maaramuutos-alkutiedot []
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2025 2030))

        valittu-hoitokausi (nth hoitokaudet 3)
        hae-maaramuutokset-fn #(kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-tehtava-maaramuutokset
                                 +kayttaja-jvh+
                                 %)

        maaramuutokset (hae-maaramuutokset-fn {:urakka-id urakka-id
                                               :hoitokaudet hoitokaudet
                                               :valittu-hoitokausi valittu-hoitokausi
                                               :laskenta-automatiikka? true})
        ;; Bäkkärissä lisätään gridiin väliotsikot 
        ;; otetaan ne pois, palautetaan raaka data 
        maaramuutokset-ei-valiotsikoita (filter #(not (:valiotsikko %)) maaramuutokset)]

    maaramuutokset-ei-valiotsikoita))


(defn- tallenna-maaramuutokset [rivi tallenna-yksikkohinta?]
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2025 2030))
        valittu-hoitokausi (nth hoitokaudet 3)

        params {:urakka-id urakka-id
                :hoitokaudet hoitokaudet
                :valittu-hoitokausi valittu-hoitokausi
                :laskenta-automatiikka? true}

        ;; Testataan molemmant endpointit tällä 
        ;; Ainoa mikä parametreissa muuttuu, on rivi 
        ;; tallenna-maaramuutos-yksikkohinta =>  passataan pelkkä rivi {..}
        ;; tallenna-tehtava-maaramuutokset => passataan muokatut rivit grid vectorina [{..}]
        params (merge params
                 (if tallenna-yksikkohinta?
                   {:rivi rivi}
                   {:rivit rivi}))

        endpoint (if tallenna-yksikkohinta?
                   :tallenna-maaramuutos-yksikkohinta
                   :tallenna-tehtava-maaramuutokset)]

    (kutsu-palvelua (:http-palvelin jarjestelma)
      endpoint
      +kayttaja-jvh+
      params)))


(deftest hae-tehtava-maaramuutokset-toimii
  (let [maaramuutokset (hae-maaramuutos-alkutiedot)
        reunapaalujen-uusiminen (first
                                  (filter #(= (:tehtava %) "Opastustaulun/-viitan uusiminen") maaramuutokset))
        suunniteltu (-> reunapaalujen-uusiminen :suunniteltu_maara)
        kulut (-> reunapaalujen-uusiminen :kirjatut_kulut_summa)
        toteumat (-> reunapaalujen-uusiminen :maara)
        maaramuutos (-> reunapaalujen-uusiminen :maaramuutos)
        tav-hinta-muutos (-> reunapaalujen-uusiminen :tavoitehinnan_muutos)
        yksikkohinta (-> reunapaalujen-uusiminen :yksikkohinta)
        toimenpide (-> reunapaalujen-uusiminen :toimenpide)]


    (testing "Tehtävä määrähaku palauttaa vastauksen"
      (is (= toimenpide
            "2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen")
        "Toimenpide nimi täsmää")

      (is (> suunniteltu 0M) "Suunniteltu määrä palauttaa arvon")
      (is (> kulut 0M) "Kirjatut kulut palauttaa arvon")
      (is (> toteumat 0M) "Toteumat palauttaa arvon")
      (is (> maaramuutos 0M) "Määrämuutos palauttaa arvon")
      (is (> tav-hinta-muutos 0M) "Tavoitehinnan muutos palauttaa arvon")
      (is (> yksikkohinta 0M) "Yksikköhinta palauttaa arvon"))


    (testing "Tehtävä- määrämuutosten sarakkeet lasketaan kaavojen mukaan"
      ;; Määrämuutos  =  Toteutunut määrä - suunniteltu määrä 
      ;; Tavoitehinnan muutos = Määrämuutos * yksikköhinta
      ;; Yksikköhinta =  Kirjatut kulut / toteutunut määrä   
      (is (= maaramuutos
            (- toteumat suunniteltu)) "Määrämuutos = toteumat - suunniteltu")

      (is (= tav-hinta-muutos
            (* maaramuutos yksikkohinta)) "Tavoitehinnan muutos = määrämuutos * yksikköhinta")

      (is (= yksikkohinta
            (/ kulut toteumat)) "Yksikköhinta = kulut / toteumat"))


    (testing "Reunapaalujen uusiminen vastaa testidataa"
      (is (= suunniteltu 6M))
      (is (= kulut 355M))
      (is (= toteumat 10M))
      (is (= maaramuutos 4M))
      (is (= tav-hinta-muutos 142.0M))
      (is (= yksikkohinta 35.5M)))))


(deftest hae-tehtava-maaramuutokset-logiikka-toimii
  (let [;; Tyhjennä data kokonaan, jäljelle jää vaan suunnitellut määrät
        _ (i "TRUNCATE TABLE kulu CASCADE;")
        _ (i "TRUNCATE TABLE kulu_kohdistus CASCADE;")
        _ (i "TRUNCATE TABLE toteuma CASCADE;")
        _ (i "TRUNCATE TABLE toteuma_tehtava CASCADE;")

        ;; Pitäisi pitkälti näyttää nollaa
        maaramuutokset-tyhja-kanta (hae-maaramuutos-alkutiedot)
        opastustaulun-uusiminen (first
                                  (filter #(= (:tehtava %) "Opastustaulun/-viitan uusiminen") maaramuutokset-tyhja-kanta))
        tehtava_id (-> opastustaulun-uusiminen :tehtava_id)

        lisatty-kulu 77777
        lisatty-toteuma 10
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        sopimus-id (hae-kajaani-hoitourakan-2025-2030-sopimus-id)

        ;; Lisää toteuma 
        _ (i (format
               "INSERT INTO toteuma 
                (
                luoja, lahde, urakka, sopimus, 
                luotu, alkanut, paattynyt, 
                suorittajan_ytunnus, suorittajan_nimi, tyyppi, lisatieto
                ) 
                VALUES (
                %s, 'harja-ui'::lahde, %s, %s, 
                '2028-11-30 17:00:00.000000', '2028-11-30 17:00:00.000000', '2028-11-30 18:05:00.000000', 
                NULL, NULL, 'kokonaishintainen', '[Muutokset] Määrämitattava toteuma 1'
                );"
               (:id +kayttaja-jvh+) urakka-id sopimus-id))

        _ (i (format
               "INSERT INTO toteuma_tehtava (
                luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi
                ) 
                VALUES (
                %s, (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 1'), 
                '2028-11-30 17:00:00.000000', %s, %s, %s, '[Muutokset] Määrämitattava toteuma 1', %s);"
               (:id +kayttaja-jvh+) tehtava_id lisatty-toteuma urakka-id 2028))

        ;; Lisää kulu tehtävälle 
        _ (i (format
               "INSERT INTO kulu 
                (
                kokonaissumma, erapaiva, urakka,
                luotu, luoja, muokattu, muokkaaja, poistettu,
                laskun_numero, lisatieto, koontilaskun_kuukausi
                ) 
                VALUES ( 
                %s, '2029-06-01', %s,
                '2028-09-01 14:18:52.450004', %s, NULL, NULL, false,
                NULL, '[Muutokset] Määrämitattava', 'kesakuu/4-hoitovuosi'
                );"
               lisatty-kulu urakka-id (:id +kayttaja-jvh+)))

        toimenpide-instanssi (hae-toimenpideinstanssi-id-nimella "POP MHU Kajaani 2025-2030 Liikenneympäristön hoito TP")
        tehtavaryhma (ffirst (q "select id from tehtavaryhma where yksiloiva_tunniste = '87a3bd38-ae0a-4c74-ad0d-38a6d5d512ad'"))

        _ (i (format
               "INSERT INTO kulu_kohdistus 
                ( 
                rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi,
                luotu, luoja, muokattu, muokkaaja, poistettu,
                lisatyon_lisatieto, rahavaraus_id, tyyppi, tavoitehintainen,tehtava 
                ) 
                VALUES ( 
                0, (SELECT id FROM kulu WHERE kokonaissumma = %s AND erapaiva = '2029-06-01'),
                %s, %s, %s, 'kokonaishintainen',
                '2028-09-01 14:18:52.450', %s, NULL, NULL, false,
                NULL, NULL, 'hankintakulu', true, %s
                );"
               lisatty-kulu lisatty-kulu
               toimenpide-instanssi tehtavaryhma (:id +kayttaja-jvh+) tehtava_id))

        maaramuutokset-jalkeen (hae-maaramuutos-alkutiedot)

        tehtavan-maaramuutokset (first
                                  (filter #(= (:tehtava_id %) tehtava_id) maaramuutokset-jalkeen))

        suunniteltu (-> tehtavan-maaramuutokset :suunniteltu_maara)
        kulut (-> tehtavan-maaramuutokset :kirjatut_kulut_summa)
        toteumat (-> tehtavan-maaramuutokset :maara)
        maaramuutos (-> tehtavan-maaramuutokset :maaramuutos)
        tav-hinta-muutos (-> tehtavan-maaramuutokset :tavoitehinnan_muutos)
        yksikkohinta (-> tehtavan-maaramuutokset :yksikkohinta)]


    (testing "Alkutiedot näyttävät nollaa"
      ;; Looppaile kaikki rivit läpi
      (is (every? (fn [rivi]
                    (and
                      (= (:maara rivi) 0M)
                      (= (:yksikkohinta rivi) 0.0)
                      (= (:kirjatut_kulut_summa rivi) 0M)
                      (= (:tavoitehinnan_muutos rivi) 0.0)
                      (true? (:anna-kirjata-tavoitehinta? rivi))))
            maaramuutokset-tyhja-kanta)))


    ;; Testaa että kaavat täsmäävät juuri lisäämän toteuman, sekä kulun perusteella
    (testing "Määrämuutokset lasketaan oikein toteuman sekä kulun lisäyksen jälkeen"
      (is (= suunniteltu 6M))
      (is (= kulut (bigdec lisatty-kulu)))
      (is (= toteumat (bigdec lisatty-toteuma)))
      (is (= maaramuutos 4M))
      (is (= tav-hinta-muutos 31110.8M))
      (is (= yksikkohinta 7777.7M))
      (is (false? (:anna-kirjata-tavoitehinta? tehtavan-maaramuutokset)))

      (is (= maaramuutos
            (- toteumat suunniteltu)) "Määrämuutos = toteumat - suunniteltu")
      (is (= tav-hinta-muutos
            (* maaramuutos yksikkohinta)) "Tavoitehinnan muutos = määrämuutos * yksikköhinta")
      (is (= yksikkohinta
            (/ kulut toteumat)) "Yksikköhinta = kulut / toteumat"))))


(deftest tallenna-tehtava-maaramuutokset-toimii
  (let [maaramuutokset (hae-maaramuutos-alkutiedot)
        ;; --------------------------------------------------------
        ;; Haetut 
        opastetaulun-uusiminen (first
                                 (filter #(= (:tehtava %) "Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)") maaramuutokset))
        suunniteltu (-> opastetaulun-uusiminen :suunniteltu_maara)
        kulut (-> opastetaulun-uusiminen :kirjatut_kulut_summa)
        toteumat (-> opastetaulun-uusiminen :maara)
        maaramuutos (-> opastetaulun-uusiminen :maaramuutos)
        tav-hinta-muutos (-> opastetaulun-uusiminen :tavoitehinnan_muutos)
        yksikkohinta (-> opastetaulun-uusiminen :yksikkohinta)
        aikaisemmat-yksikkohinnat (-> opastetaulun-uusiminen :aikaisemmat-yksikkohinnat)
        anna-kirjata? (-> opastetaulun-uusiminen :anna-kirjata-tavoitehinta?)
        versio-ennen (-> opastetaulun-uusiminen :versio)
        ;; Valitse yksikköhinta, ja tallenna se 
        tallenna-yksikkohinta? true
        ;; Valitse ensimmäinen alasvedossa oleva yksikköhinta 
        yksikkohinta-valinta (-> aikaisemmat-yksikkohinnat first :arvo)
        yksikkohinta-valinta-hk (-> aikaisemmat-yksikkohinnat first :hoitokauden-alkuvuosi)


        ;; ----------------------------------------
        ;; Yksikköhinta tallennus 
        tallenna-rivi (-> opastetaulun-uusiminen
                        ;; Lisää riviin yksikköhinta, sekä sen alkuvuosi, ja kutsu tähän tallenna 
                        (assoc
                          ;; Yksikköhinta valitaan -> lähde on valittu 
                          :yksikkohinnan_lahde "valittu"
                          :yksikkohinta yksikkohinta-valinta
                          :yksikkohinnan_alkuvuosi yksikkohinta-valinta-hk))

        _ (tallenna-maaramuutokset tallenna-rivi tallenna-yksikkohinta?)

        ;; --------------------------------------------------------
        ;; Tallennetut (yksikköhinta)
        tallennetut-maaramuutokset (hae-maaramuutos-alkutiedot)
        opastetaulun-uusiminen-tallennettu (first (filter #(= (:tehtava %) "Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)") tallennetut-maaramuutokset))
        maaramuutos-t (-> opastetaulun-uusiminen-tallennettu :maaramuutos)
        tav-hinta-muutos-t (-> opastetaulun-uusiminen-tallennettu :tavoitehinnan_muutos)
        yksikkohinta-t (-> opastetaulun-uusiminen-tallennettu :yksikkohinta)
        suunniteltu-t (-> opastetaulun-uusiminen-tallennettu :suunniteltu_maara)
        toteumat-t (-> opastetaulun-uusiminen-tallennettu :maara)
        versio-jalkeen (-> opastetaulun-uusiminen-tallennettu :versio)

        ;; --------------------------------------------------------
        ;; Grid tallennus 
        tallenna-yksikkohinta? false
        runkopuiden-poisto (first (filter #(= (:tehtava %) "Runkopuiden poisto") maaramuutokset))
        palteiden-poisto (first (filter #(= (:tehtava %) "Päällystettyjen teiden palteiden poisto") maaramuutokset))
        maakiven-poisto (first (filter #(= (:tehtava %) "Maakivien (>1m3) poisto") maaramuutokset))
        puuttuu-muutos 123123123
        syy-1 "Muutoksia 1"
        syy-2 "Muutoksia 2"
        syy-3 "Muutoksia 3"

        ;; Laitetaan rivit grid dataksi, eli vec [ {..} {..} ]
        rivit (vec (conj []
                     (assoc runkopuiden-poisto
                       :syy syy-1
                       :tavoitehinnan_muutos puuttuu-muutos
                       :yksikkohinnan_lahde "puuttuu")

                     (assoc palteiden-poisto :syy syy-2)
                     (assoc maakiven-poisto :syy syy-3)))

        _ (tallenna-maaramuutokset rivit tallenna-yksikkohinta?)

        tallennetut-grid (hae-maaramuutos-alkutiedot)
        runkopuiden-poisto-t (first (filter #(= (:tehtava %) "Runkopuiden poisto") tallennetut-grid))
        palteiden-poisto-t (first (filter #(= (:tehtava %) "Päällystettyjen teiden palteiden poisto") tallennetut-grid))
        maakiven-poisto-t (first (filter #(= (:tehtava %) "Maakivien (>1m3) poisto") tallennetut-grid))

        runko-lahde (-> runkopuiden-poisto-t :yksikkohinnan_lahde)
        runko-tav-hinta (-> runkopuiden-poisto-t :syotetty_tavoitehintamuutos)
        runko-syy (-> runkopuiden-poisto-t :syy)
        palteet-syy (-> palteiden-poisto-t :syy)
        maakivi-syy (-> maakiven-poisto-t :syy)

        ;; Versiointi 
        rivit (vec (conj []
                     (assoc runkopuiden-poisto
                       :syy "test versio"
                       :tavoitehinnan_muutos puuttuu-muutos
                       :yksikkohinnan_lahde "puuttuu")
                     (assoc palteiden-poisto :syy "test versio")
                     (assoc maakiven-poisto :syy "test versio")))

        _ (tallenna-maaramuutokset rivit tallenna-yksikkohinta?)

        tallennetut-grid (hae-maaramuutos-alkutiedot)
        runkopuiden-poisto-t (first (filter #(= (:tehtava %) "Runkopuiden poisto") tallennetut-grid))
        palteiden-poisto-t (first (filter #(= (:tehtava %) "Päällystettyjen teiden palteiden poisto") tallennetut-grid))
        maakiven-poisto-t (first (filter #(= (:tehtava %) "Maakivien (>1m3) poisto") tallennetut-grid))

        versio-runkopuut (-> runkopuiden-poisto-t :versio)
        versio-palteet (-> palteiden-poisto-t :versio)
        versio-maakivet (-> maakiven-poisto-t :versio)]


    (testing "Opastetaulun uusiminen vastaa testidataa"
      (is (= suunniteltu 20M))
      (is (= kulut 0M))
      (is (= toteumat 0M))
      (is (= maaramuutos -20M))
      (is (= tav-hinta-muutos 0.0))
      (is (= yksikkohinta 0.0))
      (is (= 2 (count aikaisemmat-yksikkohinnat)))
      (is (false? anna-kirjata?)))


    (testing "Yksikköhinnan tallennus toimii"
      (is (= (-> opastetaulun-uusiminen-tallennettu :yksikkohinnan_alkuvuosi) yksikkohinta-valinta-hk)
        "Rivi vastaa tallennettua arvoa")
      (is (= (-> opastetaulun-uusiminen-tallennettu :yksikkohinnan_lahde) "valittu") "Lähde on valittu (yksikköhinta valittu)"))


    (testing "Kaavat täsmää yksikköhinnan valinnan jälkeen"
      (is (= maaramuutos-t
            (- toteumat-t suunniteltu-t)) "Määrämuutos = toteumat - suunniteltu")
      (is (= tav-hinta-muutos-t
            (* maaramuutos-t yksikkohinta-t)) "Tavoitehinnan muutos = määrämuutos * yksikköhinta"))


    (testing "Grid tallennus toimii"
      (is (= runko-lahde "puuttuu"))
      (is (= runko-tav-hinta (bigdec puuttuu-muutos)))
      (is (= runko-syy syy-1))
      (is (= palteet-syy syy-2))
      (is (= maakivi-syy syy-3)))

    (testing "Versiointi toimii"
      (is (= versio-ennen nil))
      (is (= versio-jalkeen 1))
      (is (= versio-runkopuut 2))
      (is (= versio-palteet 2))
      (is (= versio-maakivet 2)))))
