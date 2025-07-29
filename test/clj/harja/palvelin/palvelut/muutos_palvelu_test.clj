(ns harja.palvelin.palvelut.muutos-palvelu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-urakan-muutostiedot (component/using
                                                   (muutos-palvelu/->Muutos {:kehitysmoodi true})
                                                   [:http-palvelin :db])
                        :hae-muutoksen-tiedot (component/using
                                                (muutos-palvelu/->Muutos {:kehitysmoodi true})
                                                [:http-palvelin :db])
                        :tallenna-muutos (component/using
                                                   (muutos-palvelu/->Muutos {:kehitysmoodi true})
                                                   [:http-palvelin :db])
                        :tallenna-rahavarausmuutosten-syyt (component/using
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
        toimenpide-id-paall-paikk (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '20107';")) ; Päällystepaikkaukset
        toimenpide-id-soratiet (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '23124';")) ; Soratiet
        toimenpide-id-mhu-yllapito (ffirst (q "SELECT id FROM toimenpide WHERE koodi = '20191';")) ; -- MHU Ylläpito
        liite-id (ffirst (q "SELECT id FROM liite WHERE nimi = 'rumpu.jpg'"))
        vastaus (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :valittu-hoitokausi valittu-hoitokausi})
        odotetut-kirjatut-muutokset [{:kulu_kohdistus nil, :kustannusvaikutukset (list {:summa 1000, :toimenpide 2391, :kustannuslaji "hankintakustannukset"}),
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot.",
                                      :tehtavat_ja_maarat (list {:tehtava 3117, :uusi_maara 1100, :maaramuutos 100, :edellinen_maara 1000}), :urakka 36, :nimi "Päällysteen paikkausmuutos",
                                      :id 1, :jjh-muutosten-summa nil, :liitteet nil, :versio 1, :luonnos false, :tavoitehinnan-muutos 1000, :tyyppi "pysyva"}
                                     {:kulu_kohdistus nil, :kustannusvaikutukset (list {:summa 3000, :toimenpide 608, :kustannuslaji "hankintakustannukset"}),
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua.",
                                      :tehtavat_ja_maarat nil, :urakka 36, :nimi "Erillisrahoitettu sorastusmuutos",
                                      :id 2, :jjh-muutosten-summa nil, :liitteet nil, :versio 1, :luonnos false, :tavoitehinnan-muutos 3000, :tyyppi "erillisrahoitettu"}
                                     {:kulu_kohdistus nil, :kustannusvaikutukset (list {:summa 1000, :toimenpide 700, :kustannuslaji "hankintakustannukset"}),
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.",
                                      :tehtavat_ja_maarat (list {:tehtava 1406, :uusi_maara 0, :maaramuutos -40, :edellinen_maara 40} {:tehtava 3029, :uusi_maara 0, :maaramuutos -30, :edellinen_maara 30}),
                                      :urakka 36, :nimi "Tämän hoitovuoden määräpoikkeamamuutos",
                                      :id 3, :jjh-muutosten-summa nil, :liitteet (list {:id 11, :muutos 3}), :versio 1, :luonnos false, :tavoitehinnan-muutos 1000, :tyyppi "maarapoikkeama"}
                                     {:kulu_kohdistus nil, :kustannusvaikutukset nil,
                                      :voimassa_alkaen #inst "2025-06-24T21:00:00.000-00:00", :syy "Työmääräarviot ylittyivät",
                                      :tehtavat_ja_maarat nil, :urakka 36, :nimi nil,
                                      :id 4, :jjh-muutosten-summa 1230M, :liitteet nil, :versio 1, :luonnos false, :tavoitehinnan-muutos 1230M, :tyyppi "johto-ja-hallintokorvaus"}]]

    (is (= (count (:kirjatut-muutokset vastaus)) 4) "oikea määrä muutoksia")
    (is (every? (fn [rivi] (some #(= rivi %) (:kirjatut-muutokset vastaus))) odotetut-kirjatut-muutokset)
      "Kaikki muutosrivit löytyvät vastausjoukosta")))


(deftest hae-urakan-rahavarausten-muutostiedot-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]
        odotetut-rahavarausten-muutokset
        [{:id 1, :toteumat 100000M, :nimi "Äkilliset hoitotyöt", :summa-indeksikorjattu 133200M, :tavoitehinnan-muutos -33200M}
         {:id 2, :toteumat 1000M, :nimi "Vahinkojen korjaukset", :summa-indeksikorjattu 2640M, :tavoitehinnan-muutos -1640M}
         {:id 3, :nimi "Tilaajan rahavaraus kannustinjärjestelmään", :summa-indeksikorjattu nil}
         {:id :yhteenveto, :summa-indeksikorjattu 135840M, :toteumat 101000M, :tavoitehinnan-muutos -34840M}]
        vastaus (:rahavarausten-muutokset (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                   :valittu-hoitokausi valittu-hoitokausi}))]
    (is (= (count vastaus) 4) "Rahavarausten muutokset: oikea määrä rivejä")
    (is (some #{:yhteenveto} (mapv :id vastaus)) "Rahavarausten muutokset: yhteenveto löytyy")
    (is (= vastaus odotetut-rahavarausten-muutokset) "Rahavarausten muutokset: koko lista odotettuja arvoja")))


(deftest hae-urakan-tavoitehinta-muutosten-kokonaissumma-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        valittu-hoitokausi [(pvm/->pvm "1.10.2025") (pvm/->pvm "30.09.2026")]

        vastaus (get-in
                  (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                          :valittu-hoitokausi valittu-hoitokausi})
                  [:budjettitavoitteet :muutosten-vaikutus-yhteensa])]
    (is (= vastaus -28610M) "Muutosten vaikutus yhteensä")))

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
        odotetut-rivit []]
    (is (= (count (:kirjatut-muutokset vastaus-22-23)) 0) "oikea määrä muutoksia 22-23")
    (is (= (count (:kirjatut-muutokset vastaus-23-24)) 0) "oikea määrä muutoksia 23-24")
    (is (= (count (:kirjatut-muutokset vastaus-24-25)) 0) "oikea määrä muutoksia 24-25")
    (is (= odotetut-rivit (:kirjatut-muutokset vastaus-22-23) (:kirjatut-muutokset vastaus-23-24) (:kirjatut-muutokset vastaus-24-25)))))


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
        odotetut-luonnin-jalkeen [{:id                    1
                                   :nimi                  "Äkilliset hoitotyöt"
                                   :summa-indeksikorjattu 133200M
                                   :syy                   "Tämä on syy 1"
                                   :tavoitehinnan-muutos  -33200M
                                   :toteumat              100000M}
                                  {:id                    2
                                   :nimi                  "Vahinkojen korjaukset"
                                   :summa-indeksikorjattu 2640M
                                   :syy                   "Tämä on syy 2"
                                   :tavoitehinnan-muutos  -1640M
                                   :toteumat              1000M}
                                  {:id                    3
                                   :nimi                  "Tilaajan rahavaraus kannustinjärjestelmään"
                                   :summa-indeksikorjattu nil
                                   :syy                   "Tämä on syy 3"}
                                  {:id                    :yhteenveto
                                   :summa-indeksikorjattu 135840M
                                   :tavoitehinnan-muutos  -34840M
                                   :toteumat              101000M}]
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
        odotetut-muokkauksen-jalkeen  [{:id                    1
                                        :nimi                  "Äkilliset hoitotyöt"
                                        :summa-indeksikorjattu 133200M
                                        :syy                   "Tämä on syy 1 muokattuna"
                                        :tavoitehinnan-muutos  -33200M
                                        :toteumat              100000M}
                                       {:id                    2
                                        :nimi                  "Vahinkojen korjaukset"
                                        :summa-indeksikorjattu 2640M
                                        :syy                   "Tämä on syy 2"
                                        :tavoitehinnan-muutos  -1640M
                                        :toteumat              1000M}
                                       {:id                    3
                                        :nimi                  "Tilaajan rahavaraus kannustinjärjestelmään"
                                        :summa-indeksikorjattu nil
                                        :syy                   "Tämä on syy 3"}
                                       {:id                    :yhteenveto
                                        :summa-indeksikorjattu 135840M
                                        :tavoitehinnan-muutos  -34840M
                                        :toteumat              101000M}]]
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
        muutos-payload {:voimassa_alkaen #inst "2025-06-25T10:07:32.000-00:00",
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
                                        :kustannusvaikutukset nil
                                        :liitteet nil
                                        :luonnos nil
                                        :nimi nil
                                        :syy "Johtamisen tarve muuttui"
                                        :tavoitehinnan-muutos 780M
                                        :tehtavat_ja_maarat nil
                                        :tyyppi "johto-ja-hallintokorvaus"
                                        :urakka urakka-id
                                        :versio 1
                                        :voimassa_alkaen #inst"2025-06-24T21:00:00.000-00:00"})
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
                                        :kustannusvaikutukset nil
                                        :liitteet nil
                                        :luonnos nil
                                        :nimi nil
                                        :syy "Johtamisen tarve muuttui taas"
                                        :tavoitehinnan-muutos 780M
                                        :tehtavat_ja_maarat nil
                                        :tyyppi "johto-ja-hallintokorvaus"
                                        :urakka urakka-id
                                        :versio 2
                                        :voimassa_alkaen #inst"2025-06-24T21:00:00.000-00:00"})
        odotettu-historiarivi {:id              (inc max-id-ennen-tallennusta)
                               :kulu_kohdistus  nil
                               :luoja           (:id +kayttaja-jvh+)
                               :luonnos         nil
                               :nimi            nil
                               :poistettu       false
                               :syy             "Johtamisen tarve muuttui"
                               :tyyppi          "johto-ja-hallintokorvaus"
                               :urakka          36
                               :versio          1
                               :voimassa_alkaen #inst"2025-06-24T21:00:00.000-00:00"}

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

(deftest hae-yksittaisen-muutoksen-tiedot-lomakkeelle-ii
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        muutos {:id (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos WHERE syy = 'Työmääräarviot ylittyivät';")),
                :versio 1, :tyyppi "johto-ja-hallintokorvaus"}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-muutoksen-tiedot
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :muutos muutos})
        odotettu-muutostieto {:id     4
                              :kulut  [{:kulu-id (ffirst (q "SELECT id FROM kulu WHERE lisatieto = 'Muutoksesta automaattisesti luotu kulu 1'"))
                                        :pvm #inst"2025-10-15T00:00:00.000-00:00"
                                        :tavoitehinnan-muutos 1230}]
                              :liitteet nil
                              :versio 1}]
    (is (= vastaus odotettu-muutostieto) "muutoksen tiedot löytyvät onnistuneesti")))

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
