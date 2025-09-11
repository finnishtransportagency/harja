(ns harja.palvelin.palvelut.muutos-palvelu-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.liitteet :as liitteet-komponentti]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu])
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
        vastaus (hae-urakan-muutostiedot +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :valittu-hoitokausi valittu-hoitokausi})
        odotetut-kirjatut-muutokset [{:kulu_kohdistus nil, :kustannusvaikutukset (list {:summa 1000, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 90}),
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Täytyykin tehdä enemmän päällysteiden paikkausta, koska pahat kelirikot.",
                                      :tehtavat_ja_maarat (list {:tehtava 3117, :uusi_maara 1100, :maaramuutos 100, :edellinen_maara 1000}),
                                      :urakka urakka-id, :nimi "Päällysteen paikkausmuutos", :id 1, :jjh-muutosten-summa nil, :liitteet nil, :versio 1, :luonnos false, :tavoitehinnan-muutos 1000, :tyyppi "pysyva"}
                                     {:kulu_kohdistus nil, :kustannusvaikutukset (list {:summa 3000, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 89}),
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Tehdään lisäksi tämä isohko sorastus, ei ollut tiedossa ennen urakan alkua.",
                                      :tehtavat_ja_maarat nil,
                                      :urakka urakka-id, :nimi "Erillisrahoitettu sorastusmuutos", :id 2, :jjh-muutosten-summa nil, :liitteet nil, :versio 1, :luonnos false, :tavoitehinnan-muutos 3000, :tyyppi "erillisrahoitettu"}
                                     {:kulu_kohdistus nil, :kustannusvaikutukset (list {:summa 1000, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 91}),
                                      :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00", :syy "Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.",
                                      :tehtavat_ja_maarat (list {:tehtava 1406, :uusi_maara 0, :maaramuutos -40, :edellinen_maara 40} {:tehtava 3029, :uusi_maara 0, :maaramuutos -30, :edellinen_maara 30}),
                                      :urakka urakka-id, :nimi "Tämän hoitovuoden määräpoikkeamamuutos", :id 3, :jjh-muutosten-summa nil,
                                      :liitteet (list {:id 11, :muutos 3}), :versio 1, :luonnos false, :tavoitehinnan-muutos 1000, :tyyppi "maarapoikkeama"}
                                     {:kulu_kohdistus nil, :kustannusvaikutukset nil, :voimassa_alkaen #inst "2025-06-24T21:00:00.000-00:00", :syy "Työmääräarviot ylittyivät",
                                      :tehtavat_ja_maarat nil, :urakka urakka-id, :nimi nil, :id 4, :jjh-muutosten-summa 1230M, :liitteet nil, :versio 1, :luonnos false, :tavoitehinnan-muutos 1230M, :tyyppi "johto-ja-hallintokorvaus"}]]
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
    (is (= (Math/round vastaus) -37048) "Muutosten vaikutus yhteensä")))

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
                                        :kustannusvaikutukset (list {:summa nil, :kustannuslaji nil, :toimenpideinstanssi nil})
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
                                        :kustannusvaikutukset (list {:summa nil, :kustannuslaji nil, :toimenpideinstanssi nil})
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

(deftest hae-yksittaisen-muutoksen-tiedot-lomakkeelle-ii-johto-ja-hallintokorvaus
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        muutos {:id (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos WHERE syy = 'Työmääräarviot ylittyivät';")),
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

(deftest hae-yksittaisen-muutoksen-tiedot-lomakkeelle-ii-pysyva-muutos
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        muutos {:id (ffirst (q "SELECT MAX(id) FROM ONLY mhu_muutos WHERE nimi = 'Päällysteen paikkausmuutos';")),
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
                                                      {:budjetoidut_summat  (list
                                                                              {:budjetoitu_summa 120000
                                                                               :hoitokauden_alkuvuosi 2025}
                                                                              {:budjetoitu_summa 120000
                                                                               :hoitokauden_alkuvuosi 2026})
                                                       :id (:id muutos)
                                                       :kustannusvaikutukset (list {:hoitokauden_alkuvuosi 2025
                                                                                    :kustannuslaji "hankintakustannukset"
                                                                                    :summa 1000
                                                                                    :toimenpideinstanssi tpi-id-paallpaikk}
                                                                               {:hoitokauden_alkuvuosi 2026
                                                                                :kustannuslaji "hankintakustannukset"
                                                                                :summa 1000
                                                                                :toimenpideinstanssi tpi-id-paallpaikk})
                                                       :tehtavat_ja_maarat (list {:edellinen_maara 1000
                                                                                  :hoitokauden_alkuvuosi 2025
                                                                                  :maaramuutos 100
                                                                                  :tehtava 3117
                                                                                  :uusi_maara 1100}
                                                                             {:edellinen_maara 1000
                                                                              :hoitokauden_alkuvuosi 2026
                                                                              :maaramuutos 100
                                                                              :tehtava 3117
                                                                              :uusi_maara 1100})
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

(defn- muutospayload-liitteilla [muutos-id urakka-id liitteet versio]
  {:kulu_kohdistus nil,
   :kustannusvaikutukset (list
                           {:summa 1000, :toimenpide 700, :kustannuslaji "hankintakustannukset"}),
   :voimassa_alkaen #inst "2025-05-06T21:00:00.000-00:00",
   :syy "Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.",
   :tehtavat_ja_maarat (list
                         {:tehtava 1406, :uusi_maara 0, :maaramuutos -40, :edellinen_maara 40}
                         {:tehtava 3029, :uusi_maara 0, :maaramuutos -30, :edellinen_maara 30})
   :urakka urakka-id,
   :nimi "Tämän hoitovuoden määräpoikkeamamuutos",
   :id muutos-id,
   :jjh-muutosten-summa nil,
   :liitteet liitteet,
   :versio versio,
   :luonnos false,
   :kulut nil,
   :tavoitehinnan-muutos 1000,
   :tyyppi "maarapoikkeama"})

(deftest testaa-muutoksen-liitteiden-lisays-ja-poisto
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        muutos-id (ffirst (q "SELECT id FROM mhu_muutos WHERE syy = 'Ei tehdä tänä kesänä rumpuja, ovat vielä kunnossa.';"))
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
        liitelinkkien-maara-ennen-tallennusta (ffirst (q (format "SELECT COUNT(*) FROM mhu_muutos_liite WHERE muutos = %s AND versio = 1;" muutos-id)))
        muutos-payload (muutospayload-liitteilla muutos-id urakka-id liitteet 1)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-muutos
                  +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :valittu-hoitokausi valittu-hoitokausi
                   :muutos muutos-payload})
        liitelinkkien-maara-tallennuksen-jalkeen (ffirst (q (format "SELECT COUNT(*) FROM mhu_muutos_liite WHERE muutos = %s AND versio = 2;" muutos-id)))
        kirjatut (:kirjatut-muutokset vastaus)
        paivitetty (first (filter #(= (:id %) muutos-id) kirjatut))
        odotetut-liite-linkit (list {:id olemassaoleva-liite-id, :muutos muutos-id} {:id luotu-liite-id, :muutos muutos-id})
        liitteet-poistava-payload (muutospayload-liitteilla muutos-id urakka-id [] 2)
        liitteet-poistettu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :tallenna-muutos
                                     +kayttaja-jvh+
                                     {:urakka-id urakka-id
                                      :valittu-hoitokausi valittu-hoitokausi
                                      :muutos liitteet-poistava-payload})
        paivitetty-liitteet-poistettu (first (filter #(= (:id %) muutos-id) (:kirjatut-muutokset liitteet-poistettu-vastaus)))
        liitelinkkien-maara-liitteiden-poiston-jalkeen (ffirst (q (format "SELECT COUNT(*) FROM mhu_muutos_liite WHERE muutos = %s AND versio = 3;" muutos-id)))]
    (is (= 1 liitelinkkien-maara-ennen-tallennusta) "Ennen tallennusta oli yksi liitelinkki muutoksessa")
    (is (= 2 liitelinkkien-maara-tallennuksen-jalkeen) "Tallennuksen jälkeen kaksi liitelinkkiä")
    (is (= odotetut-liite-linkit (:liitteet paivitetty)) "Liitteiden linkit on päivitetty muutokseen")
    (is (= 0 liitelinkkien-maara-liitteiden-poiston-jalkeen) "Liitteiden poistamisen jälkeen ei liitelinkkejä")
    (is (nil? (:liitteet paivitetty-liitteet-poistettu)) "Ei palaudu enää liitteitä kun ne on poistettu muutoksesta.")))


(defn- hae-maaramuutos-alkutiedot []
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2021 2026))

        valittu-hoitokausi (last hoitokaudet)
        hae-maaramuutokset-fn #(kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-tehtava-maaramuutokset
                                 +kayttaja-jvh+
                                 %)

        maaramuutokset (hae-maaramuutokset-fn {:urakka-id urakka-id
                                               :hoitokaudet hoitokaudet
                                               :valittu-hoitokausi valittu-hoitokausi})
        ;; Bäkkärissä lisätään gridiin väliotsikot 
        ;; otetaan ne pois, palautetaan raaka data 
        maaramuutokset-ei-valiotsikoita (filter #(not (:valiotsikko %)) maaramuutokset)]

    maaramuutokset-ei-valiotsikoita))


(defn- tallenna-maaramuutokset [rivi tallenna-yksikkohinta?]
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitokaudet (mapv (fn [vuosi]
                            [(pvm/hoitokauden-alkupvm vuosi)
                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                      (range 2021 2026))
        valittu-hoitokausi (last hoitokaudet)

        params {:urakka-id urakka-id
                :hoitokaudet hoitokaudet
                :valittu-hoitokausi valittu-hoitokausi}
        
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
        reunapaalujen-uusiminen (first maaramuutokset)
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
      (is (= kulut 255M))
      (is (= toteumat 10M))
      (is (= maaramuutos 4M))
      (is (= tav-hinta-muutos 102.0M))
      (is (= yksikkohinta 25.5M)))))


(deftest hae-tehtava-maaramuutokset-logiikka-toimii
  (let [;; Tyhjennä data kokonaan, jäljelle jää vaan suunnitellut määrät
        _ (i "TRUNCATE TABLE kulu CASCADE;")
        _ (i "TRUNCATE TABLE kulu_kohdistus CASCADE;")
        _ (i "TRUNCATE TABLE toteuma CASCADE;")
        _ (i "TRUNCATE TABLE toteuma_tehtava CASCADE;")

         ;; Pitäisi pitkälti näyttää nollaa 
        maaramuutokset-tyhja-kanta (hae-maaramuutos-alkutiedot)
        opastustaulun-uusiminen (first maaramuutokset-tyhja-kanta)
        tehtava_id (-> opastustaulun-uusiminen :tehtava_id)

        lisatty-kulu 77777
        lisatty-toteuma 10
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (hae-iin-maanteiden-hoitourakan-2021-2026-sopimus-id)

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
                '2025-11-30 17:00:00.000000', '2025-11-30 17:00:00.000000', '2025-11-30 18:05:00.000000', 
                NULL, NULL, 'kokonaishintainen', '[Muutokset] Määrämitattava toteuma 1'
                );"
               (:id +kayttaja-jvh+) urakka-id sopimus-id))

        _ (i (format
               "INSERT INTO toteuma_tehtava (
                luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto
                ) 
                VALUES (
                %s, (SELECT id FROM toteuma WHERE lisatieto = '[Muutokset] Määrämitattava toteuma 1'), 
                '2025-11-30 17:00:00.000000', %s, %s, %s, '[Muutokset] Määrämitattava toteuma 1'
                );"
               (:id +kayttaja-jvh+) tehtava_id lisatty-toteuma urakka-id))

        ;; Lisää kulu tehtävälle 
        _ (i (format
               "INSERT INTO kulu 
                (
                kokonaissumma, erapaiva, urakka,
                luotu, luoja, muokattu, muokkaaja, poistettu,
                laskun_numero, lisatieto, koontilaskun_kuukausi
                ) 
                VALUES ( 
                %s, '2026-06-01', %s,
                '2025-09-01 14:18:52.450004', %s, NULL, NULL, false,
                NULL, '[Muutokset] Määrämitattava', 'kesakuu/5-hoitovuosi'
                );"
               lisatty-kulu urakka-id (:id +kayttaja-jvh+)))

        toimenpide-instanssi (hae-toimenpideinstanssi-id-nimella "Iin MHU 2021-2026 Liikenneympäristön hoito TP")
        tehtavaryhma (ffirst (q "select id from tehtavaryhma where yksiloiva_tunniste = '87a3bd38-ae0a-4c74-ad0d-38a6d5d512ad'"))

        _ (i (format
               "INSERT INTO kulu_kohdistus 
                ( 
                rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi,
                luotu, luoja, muokattu, muokkaaja, poistettu,
                lisatyon_lisatieto, rahavaraus_id, tyyppi, tavoitehintainen,tehtava 
                ) 
                VALUES ( 
                0, (SELECT id FROM kulu WHERE kokonaissumma = %s AND erapaiva = '2026-06-01'),
                %s, %s, %s, 'kokonaishintainen',
                '2025-09-01 14:18:52.450', %s, NULL, NULL, false,
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
        opastetaulun-uusiminen (second maaramuutokset)
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
        opastetaulun-uusiminen-tallennettu (second tallennetut-maaramuutokset)
        maaramuutos-t (-> opastetaulun-uusiminen-tallennettu :maaramuutos)
        tav-hinta-muutos-t (-> opastetaulun-uusiminen-tallennettu :tavoitehinnan_muutos)
        yksikkohinta-t (-> opastetaulun-uusiminen-tallennettu :yksikkohinta)
        suunniteltu-t (-> opastetaulun-uusiminen-tallennettu :suunniteltu_maara)
        toteumat-t (-> opastetaulun-uusiminen-tallennettu :maara)
        versio-jalkeen (-> opastetaulun-uusiminen-tallennettu :versio)

        ;; --------------------------------------------------------
        ;; Grid tallennus 
        tallenna-yksikkohinta? false
        runkopuiden-poisto (nth maaramuutokset 2)
        palteiden-poisto (nth maaramuutokset 3)
        maakiven-poisto (nth maaramuutokset 4)
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
        runkopuiden-poisto-t (nth tallennetut-grid 2)
        palteiden-poisto-t (nth tallennetut-grid 3)
        maakiven-poisto-t (nth tallennetut-grid 4)

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
        runkopuiden-poisto-t (nth tallennetut-grid 2)
        palteiden-poisto-t (nth tallennetut-grid 3)
        maakiven-poisto-t (nth tallennetut-grid 4)
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
