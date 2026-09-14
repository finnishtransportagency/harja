(ns harja.palvelin.raportointi.vastaanottotarkastus-mhu-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]

            [harja.palvelin.komponentit.tietokanta :as tietokanta]

            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti :as muutos-ja-lisatyoraportti]
            [harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara :as talvisuola]
            [harja.palvelin.raportointi.raportit :as raportit]
            [harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu :as vastaanottotarkastus-mhu]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae-urakan-lupaustiedot (component/using
                                     (lupaus-palvelu/->Lupaus {:kehitysmoodi true})
                                     [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)

;; Meillä on kaksi vastaantottotarkastusraporttia, joista toinen on päällystysurakoille ja toinen MHU-urakoille.
;; Testataan, että ne ovat rekisteröityinä eri urakkatyyppien alle ja että ne eroavat toisistaan.
(deftest vastaanottotarkastusraportit-ovat-erilliset
  (let [paallystys (get raportit/raportit-nimen-mukaan :vastaanottotarkastusraportti)
        mhu (get raportit/raportit-nimen-mukaan :vastaanottotarkastusraportti-mhu)]
    (testing "molemmat raportit ovat rekisteröityinä"
      (is (some? paallystys))
      (is (some? mhu)))
    (testing "raportit on kohdistettu eri urakkatyypeille"
      (is (= #{:paallystys} (:urakkatyyppi paallystys)))
      (is (= #{:teiden-hoito} (:urakkatyyppi mhu))))
    (testing "MHU-raportti tarvitsee yhden urakan"
      (is (= #{"urakka"} (:konteksti mhu)))
      (is (= #{:teiden-hoito} (:urakkatyyppi mhu)))
      (is (= "Vastaanottotarkastusraportti - MHU" (:kuvaus mhu))))
    (testing "raporteilla on eri tunnisteet ja toteutukset"
      (is (not= (:nimi paallystys) (:nimi mhu)))
      (is (not= (:suorita paallystys) (:suorita mhu))))))

(def ^:private testi-hoitokaudet
  [{:alkupvm #inst "2021-10-01T00:00:00.000-00:00"
    :loppupvm #inst "2022-09-30T23:59:59.000-00:00"}
   {:alkupvm #inst "2022-10-01T00:00:00.000-00:00"
    :loppupvm #inst "2023-09-30T23:59:59.000-00:00"}])

(def ^:private testi-talvisuolan-erittely
  [:taulukko {:otsikko "Erittely hoitovuosittain"} [] []])

(defn- muodosta-testiraportti []
  (let [lupaustiedot (fn [_ {:keys [valittu-hoitokausi]}]
                       (if (= (first valittu-hoitokausi) #inst "2021-10-01T00:00:00.000-00:00")
                         {:lupaus-sitoutuminen {:pisteet 70}
                          :yhteenveto {:pisteet {:toteuma 65}}}
                         {:lupaus-sitoutuminen {:pisteet 80}
                          :yhteenveto {:pisteet {:toteuma 75}}}))
        talvisuolan-erittely testi-talvisuolan-erittely]
    (with-redefs [urakat-q/hae-urakka (fn [_ _] [{:nimi "Testiurakka"
                                                  :alkupvm #inst "2021-01-01T00:00:00.000-00:00"
                                                  :loppupvm #inst "2023-12-31T23:59:59.000-00:00"}])
                  urakat-q/hae-urakan-hoitokaudet (fn [_ _] testi-hoitokaudet)
                  urakat-q/hae-urakan-parametrit (fn [_ _] [{:muutosten_hallinta false}])
                  muutos-ja-lisatyoraportti/hae-tavoitehinnan-oikaisut (fn [_ _] [])
                  muutos-ja-lisatyoraportti/hae-lisatoiden-kulukohdistukset (fn [_ _] [])
                  talvisuola/suorita (fn [_ _ _]
                                       [:raportti {}
                                        [:taulukko {:otsikko "Koko urakka-ajan yhteenveto (kuivatonneina)"}
                                         []
                                         [["Suurin urakassa sallittu käyttömäärä + 5 %"
                                           [:arvo {:arvo 1050M}]]]]
                                        talvisuolan-erittely])
                  materiaalit-kyselyt/hae-talvisuolan-kokonaismaara
                  (fn [_ _] [{:kokonaismaara 1000M}])
                  lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle lupaustiedot
                  valikatselmus-q/hae-bonukset (fn [_ {:keys [alkupvm]}]
                                                 (if (= alkupvm (-> testi-hoitokaudet first :alkupvm))
                                                   [{:rahasumma 100M}]
                                                   [{:rahasumma 200M}]))
                  valikatselmus-q/hae-sanktiot (fn [_ {:keys [alkupvm]}]
                                                 (cond
                                                   (= alkupvm (-> testi-hoitokaudet first :alkupvm))
                                                   [{:maara -25M}]
                                                   (= alkupvm #inst "2021-01-01T00:00:00.000-00:00")
                                                   [{:sakkoryhma :talvisuolan_ylitys :maara 100M}]
                                                   :else
                                                   [{:maara -50M}]))
                  rahavaraus-kyselyt/hae-urakan-rahavaraukset
                  (fn [_ _]
                    [{:id 1 :nimi "Äkilliset hoitotyöt"}
                     {:id 2 :nimi "Vahinkojen korjaukset"}
                     {:id 3 :nimi "Tilaajan rahavaraus kannustinjärjestelmään"}])
                  rahavaraus-kyselyt/muutosten-rahavaraukset
                  (fn [_ _ hoitokauden-alkuvuosi]
                    (if (= hoitokauden-alkuvuosi 2021)
                      [{:id 1 :summa-indeksikorjattu 100M :toteumat 80M :tavoitehinnan-muutos -20M}
                       {:id 2 :summa-indeksikorjattu 50M :toteumat 40M :tavoitehinnan-muutos -10M}
                       {:id 3 :summa-indeksikorjattu 25M :toteumat 20M :tavoitehinnan-muutos -5M}
                       {:id :yhteenveto :summa-indeksikorjattu 175M :toteumat 140M :tavoitehinnan-muutos -35M}]
                      [{:id 1 :summa-indeksikorjattu 200M :toteumat 150M :tavoitehinnan-muutos -50M}
                       {:id 2 :summa-indeksikorjattu 100M :toteumat 90M :tavoitehinnan-muutos -10M}
                       {:id 3 :summa-indeksikorjattu 50M :toteumat 45M :tavoitehinnan-muutos -5M}
                       {:id :yhteenveto :summa-indeksikorjattu 350M :toteumat 285M :tavoitehinnan-muutos -65M}]))]
      (vastaanottotarkastus-mhu/suorita nil nil {:urakka-id 1}))))

(deftest raportti-sisaltaa-lupaukset-hoitovuosittain
  (let [raportti (muodosta-testiraportti)]
    (is (= [:taulukko
            {:otsikko "Lupaukset" :sheet-nimi "Lupaukset" :tyhja nil}
            [{:otsikko "Hoitovuosi" :leveys 5}
             {:otsikko "Tarjouksen lupauspisteet" :leveys 5}
             {:otsikko "Toteutuneet lupauspisteet" :leveys 5}
             {:otsikko "Bonus/Sanktiot (€)" :leveys 5 :fmt :raha}]
            [["2021-2022" 70 65 75M]
             ["2022-2023" 80 75 150M]]]
          (nth raportti 2)))))

(deftest raportti-sisaltaa-talvisuolan-kokonaiskayttomaaran
  (let [raportti (muodosta-testiraportti)]
    (is (= [:otsikko "Talvisuolan kokonaiskäyttömäärä"]
          (nth raportti 3)))
    (is (= ["Kohtuullistettu käyttöraja + 5% (tonnia)" 1050M]
          (let [rivi (nth (nth (nth raportti 4) 3) 0)]
            [(first rivi) (get-in rivi [1 1 :arvo])])))
    (is (= ["Toteuma (tonnia)" 1000M]
          (let [rivi (nth (nth (nth raportti 4) 3) 1)]
            [(first rivi) (get-in rivi [1 1 :arvo])])))
    (is (= ["Erotus (tonnia)" -50M]
          (let [rivi (nth (nth (nth raportti 4) 3) 2)]
            [(first rivi) (get-in rivi [1 1 :arvo])])))
    (is (= ["Kirjattu sakon määrä (euroa)" 100M]
          (let [rivi (nth (nth (nth raportti 4) 3) 3)]
            [(first rivi) (get-in rivi [1 1 :arvo])])))
    (is (= 50
          (get-in raportti [4 1 :leveysprosentti])))
    (is (= false
          (get-in raportti [4 1 :viimeinen-rivi-yhteenveto?])))
    (is (= testi-talvisuolan-erittely
          (nth raportti 5)))
    (is (not-any? #(and (vector? %)
                     (= "Ympäristöraportti" (get-in % [1 :otsikko])))
          raportti))))

(deftest raportti-sisaltaa-rahavarausten-tavoitehinnan-muutokset
  (let [raportti (muodosta-testiraportti)]
    (is (= [:taulukko
            {:otsikko "Rahavarausten tavoitehintamuutokset"
             :sheet-nimi "Rahavarausten tavoitehintamuutokset"
             :tyhja nil
             :rivi-ennen [{:sarakkeita 1}
                          {:teksti "Äkilliset hoitotyöt"
                           :sarakkeita 2
                           :luokka "paallystys-tausta-tumma"
                           :tasaa :oikea}
                          {:teksti "Vahinkojen korjaukset"
                           :sarakkeita 2
                           :luokka "paallystys-tausta-tumma"
                           :tasaa :oikea}
                          {:teksti "Tilaajan rahavaraus kannustinjärjestelmään"
                           :sarakkeita 2
                           :luokka "paallystys-tausta-tumma"
                           :tasaa :oikea}
                          {:sarakkeita 1}]}
            [{:otsikko "Hoitokausi" :leveys 5}
             {:otsikko "Suunniteltu määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Toteutunut määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Suunniteltu määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Toteutunut määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Suunniteltu määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Toteutunut määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Tavoitehinnan muutos (€)" :leveys 5 :fmt :raha}]
            [["2021-2022" 100M 80M 50M 40M 25M 20M -35M]
             ["2022-2023" 200M 150M 100M 90M 50M 45M -65M]]]
          (nth raportti 7)))
    (is (not-any? #(and (vector? %)
                     (= "Ympäristöraportti" (get-in % [1 :otsikko])))
          raportti))))

(deftest MHU25-urakan-tavoitehinnan-muutokset-muodostuvat-kaikille-hoitovuosille
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        db (:db jarjestelma)
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        hoitovuodet (mapv #(pvm/vuosi (:alkupvm %)) hoitokaudet)
        odotetut-rivit [["2025-2026" 110M]
                        ["2026-2027" -157.5M]
                        ["2027-2028" 0]
                        ["2028-2029" 8.25M]
                        ["2029-2030" -7.5M]]
        kirjalliset {2025 [{:tyyppi "pysyva" :kustannusvaikutusten-summa 100M}
                           {:tyyppi "johto-ja-hallintokorvaus" :jjh-muutosten-summa -25M}]
                     2026 [{:tyyppi "muutostyo" :kustannusvaikutusten-summa -200M}
                           {:tyyppi "pysyva" :kustannusvaikutusten-summa 50M}]
                     2027 []
                     2028 [{:tyyppi "pysyva" :kustannusvaikutusten-summa 1.25M}
                           {:tyyppi "muutostyo" :kustannusvaikutusten-summa 2.75M}]
                     2029 [{:tyyppi "johto-ja-hallintokorvaus" :jjh-muutosten-summa -1M}]}
        maaramuutokset {2025 [{:tavoitehinnan_muutos 30M}
                              {:tavoitehinnan_muutos -10M}]
                        2026 [{:tavoitehinnan_muutos 12.5M}
                              {:tavoitehinnan_muutos nil}]
                        2027 [{:tavoitehinnan_muutos nil}]
                        2028 [{:tavoitehinnan_muutos -3.5M}
                              {:tavoitehinnan_muutos 0.5M}]
                        2029 [{:tavoitehinnan_muutos -10M}]}
        rahavarausten-muutokset {2025 15M
                                 2026 -20M
                                 2027 0
                                 2028 7.25M
                                 2029 3.5M}
        kirjalliset-kutsut (atom [])
        maaramuutos-kutsut (atom [])
        rahavaraus-kutsut (atom [])]
    (testing "Kajaanin urakalla muutosten hallinta on käytössä"
      (is (true? (:muutosten_hallinta (first (urakat-q/hae-urakan-parametrit db urakka-id))))))
    (testing "Kajaanin kaikki hoitovuodet ovat mukana"
      (is (= [2025 2026 2027 2028 2029] hoitovuodet)))
    (with-redefs [muutos-ja-lisatyoraportti/hae-kirjallisesti-sovitut-muutokset-raportille
                  (fn [_ {:keys [hoitokauden-alkuvuosi] :as parametrit}]
                    (swap! kirjalliset-kutsut conj parametrit)
                    (get kirjalliset hoitokauden-alkuvuosi))
                  muutos-palvelu/hae-tehtava-maaramuutokset
                  (fn [_ _ {:keys [valittu-hoitokausi _hoitokaudet _laskenta-automatiikka?] :as parametrit}]
                    (swap! maaramuutos-kutsut conj parametrit)
                    (get maaramuutokset (pvm/vuosi (first valittu-hoitokausi))))
                  rahavaraus-kyselyt/muutosten-rahavaraukset
                  (fn [_ _ hoitokauden-alkuvuosi]
                    (swap! rahavaraus-kutsut conj hoitokauden-alkuvuosi)
                    [{:id 1 :tavoitehinnan-muutos 999999M}
                     {:id :yhteenveto
                      :tavoitehinnan-muutos (get rahavarausten-muutokset hoitokauden-alkuvuosi)}])]
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-tavoitehinnan-muutokset
                            db +kayttaja-jvh+ urakka-id hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (testing "jokainen hoitovuosi käyttää oman vuoden kaikkia lähdearvoja"
          (is (= odotetut-rivit (vec (butlast rivit)))))
        (testing "yhteensä-rivi summaa hoitovuosien tulokset"
          (is (= ["Yhteensä" -46.75M]
                (get-in (last rivit) [:rivi]))))
        (testing "taulukon metatiedot säilyvät"
          (is (= "Harjaan kirjatut tavoitehinnan muutokset"
                (get-in taulukko [1 :otsikko])))
          (is (= "Harjaan kirjatut tavoitehinnan muutokset"
                (get-in taulukko [1 :sheet-nimi])))
          (is (= true (get-in taulukko [1 :viimeinen-rivi-yhteenveto?]))))
        (testing "haut kutsutaan kerran jokaista hoitovuotta kohden"
          (is (= hoitovuodet (mapv :hoitokauden-alkuvuosi @kirjalliset-kutsut)))
          (is (= hoitovuodet
                (mapv #(pvm/vuosi (first (:valittu-hoitokausi %))) @maaramuutos-kutsut)))
          (is (every? :laskenta-automatiikka? @maaramuutos-kutsut))
          (is (= hoitovuodet @rahavaraus-kutsut)))
        (testing "tehtävämäärämuutoksille välitetään kaikki hoitokaudet"
          (is (every? #(= (mapv (juxt :alkupvm :loppupvm) hoitokaudet)
                        (:hoitokaudet %))
                @maaramuutos-kutsut)))))))

(deftest MHU25-urakan-lisatyot-muodostuvat-kaikille-hoitovuosille
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        db (:db jarjestelma)
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        hoitovuodet (mapv #(pvm/vuosi (:alkupvm %)) hoitokaudet)
        lisatyot {2025 [{:summa 100M} {:summa 20M}]
                  2026 [{:summa -40M} {:summa 2.5M}]
                  2027 []
                  2028 [{:summa 8.25M} {:summa nil}]
                  2029 [{:summa -10M} {:summa 2.5M}]}
        odotetut-rivit [["2025-2026" 120M]
                        ["2026-2027" -37.5M]
                        ["2027-2028" 0]
                        ["2028-2029" 8.25M]
                        ["2029-2030" -7.5M]]
        haut (atom [])]
    (is (= [2025 2026 2027 2028 2029] hoitovuodet))
    (with-redefs [muutos-ja-lisatyoraportti/hae-lisatoiden-kulukohdistukset
                  (fn [_ parametrit]
                    (swap! haut conj parametrit)
                    (get lisatyot (pvm/vuosi (:alkupvm parametrit))))]
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-lisatyo-taulukko
                            db urakka-id hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (testing "Jokainen hoitovuosi käyttää oman vuoden lisätöitä"
          (is (= odotetut-rivit (vec (butlast rivit)))))
        (testing "yhteensä-rivi summaa hoitovuosien lisätyöt"
          (is (= ["Yhteensä" 83.25M]
                (get-in (last rivit) [:rivi]))))
        (testing "Taulukon metatiedot ovat oikein"
          (is (= "Lisätyöt" (get-in taulukko [1 :otsikko])))
          (is (= "Lisätyöt" (get-in taulukko [1 :sheet-nimi])))
          (is (= 50 (get-in taulukko [1 :leveysprosentti])))
          (is (= true (get-in taulukko [1 :viimeinen-rivi-yhteenveto?])))
          (is (nil? (get-in taulukko [1 :excel-alkutekstit]))))
        (testing "Haku kutsutaan kerran hoitovuotta kohden oikealla urakalla ja aikavälillä"
          (is (= (count hoitokaudet) (count @haut)))
          (is (every? #(= urakka-id (:urakka-id %)) @haut))
          (is (= hoitovuodet (mapv #(pvm/vuosi (:alkupvm %)) @haut))))
        (let [excel-taulukko (first (vastaanottotarkastus-mhu/muodosta-lisatyo-taulukko
                                      db urakka-id hoitokaudet :excel))]
          (testing "Excel-raportin otsikko muodostuu"
            (is (= [[:otsikko-title "Lisätyöt"]]
                  (get-in excel-taulukko [1 :excel-alkutekstit])))))))))

(defn- lisaa-testin-lisatyo-kohdistus!
  [{:keys [urakka-id toimenpideinstanssi-id tunniste erapaiva summa lisatyon-lisatieto
            tyyppi kulu-poistettu? kohdistus-poistettu?]
    :or {tyyppi "lisatyo"
         kulu-poistettu? false
         kohdistus-poistettu? false}}]
  (let [maksueratyyppi (if (= tyyppi "lisatyo") "lisatyo" "kokonaishintainen")
        kulu-id (lisaa-kulu-urakalle summa erapaiva urakka-id toimenpideinstanssi-id nil maksueratyyppi
                                      {:lisatieto tunniste
                                       :lisatyon-lisatieto lisatyon-lisatieto
                                       :kulu-poistettu? kulu-poistettu?
                                       :kohdistus-poistettu? kohdistus-poistettu?})]
    kulu-id))

(defn- siivoa-testin-lisatyot! [tunniste]
  (u (format "DELETE FROM kulu_kohdistus
               WHERE kulu IN (SELECT id FROM kulu WHERE lisatieto LIKE '%s%%')"
             tunniste))
  (u (format "DELETE FROM kulu WHERE lisatieto LIKE '%s%%'" tunniste)))

(deftest MHU25-urakan-lisatyot-suodatetaan-kyselyssa
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        toinen-urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        db (:db jarjestelma)
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        tpi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))
        toinen-tpi-id (ffirst (q (str "SELECT id FROM toimenpideinstanssi WHERE urakka = " toinen-urakka-id " LIMIT 1")))
        tunniste "vastaanottotarkastus-mhu-lisatyo-test"
        odotetut-rivit [["2025-2026" 120M]
                        ["2026-2027" -37.5M]
                        ["2027-2028" 7.25M]
                        ["2028-2029" 4.75M]
                        ["2029-2030" 7.5M]]]
    (try
      (siivoa-testin-lisatyot! tunniste)
      (doseq [[vuosi alku-summa loppu-summa]
              [[2025 100M 20M]
               [2026 -40M 2.5M]
               [2027 0M 7.25M]
               [2028 8.25M -3.5M]
               [2029 6M 1.5M]]]
        (lisaa-testin-lisatyo-kohdistus!
          {:urakka-id urakka-id
           :toimenpideinstanssi-id tpi-id
           :tunniste (str tunniste "-" vuosi "-alku")
           :erapaiva (str vuosi "-10-01")
           :summa alku-summa
           :lisatyon-lisatieto (str "Kajaani lisätyö " vuosi)} )
        (lisaa-testin-lisatyo-kohdistus!
          {:urakka-id urakka-id
           :toimenpideinstanssi-id tpi-id
           :tunniste (str tunniste "-" vuosi "-loppu")
           :erapaiva (str (inc vuosi) "-09-30")
           :summa loppu-summa
           :lisatyon-lisatieto (str "Kajaani lisätyö " vuosi)}))
      (lisaa-testin-lisatyo-kohdistus!
        {:urakka-id urakka-id
         :toimenpideinstanssi-id tpi-id
         :tunniste (str tunniste "-ennen")
         :erapaiva "2025-09-30"
         :summa 999M
         :lisatyon-lisatieto "Aikavälin ulkopuolinen lisätyö"})
      (lisaa-testin-lisatyo-kohdistus!
        {:urakka-id urakka-id
         :toimenpideinstanssi-id tpi-id
         :tunniste (str tunniste "-jalkeen")
         :erapaiva "2030-10-01"
         :summa 888M
         :lisatyon-lisatieto "Aikavälin ulkopuolinen lisätyö"})
      (lisaa-testin-lisatyo-kohdistus!
        {:urakka-id urakka-id
         :toimenpideinstanssi-id tpi-id
         :tunniste (str tunniste "-tyyppi")
         :erapaiva "2028-10-15"
         :summa 500M
         :tyyppi "hankintakulu"
         :lisatyon-lisatieto "Väärän tyypin kulu"})
      (lisaa-testin-lisatyo-kohdistus!
        {:urakka-id urakka-id
         :toimenpideinstanssi-id tpi-id
         :tunniste (str tunniste "-kohdistus-poistettu")
         :erapaiva "2028-10-16"
         :summa 600M
         :kohdistus-poistettu? true
         :lisatyon-lisatieto "Poistettu kohdistus"})
      (lisaa-testin-lisatyo-kohdistus!
        {:urakka-id urakka-id
         :toimenpideinstanssi-id tpi-id
         :tunniste (str tunniste "-kulu-poistettu")
         :erapaiva "2028-10-17"
         :summa 700M
         :kulu-poistettu? true
         :lisatyon-lisatieto "Poistettu kulu"})
      (lisaa-testin-lisatyo-kohdistus!
        {:urakka-id toinen-urakka-id
         :toimenpideinstanssi-id toinen-tpi-id
         :tunniste (str tunniste "-toinen-urakka")
         :erapaiva "2028-10-18"
         :summa 800M
         :lisatyon-lisatieto "Toisen urakan lisätyö"})
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-lisatyo-taulukko
                            db urakka-id hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (testing "vain oikean urakan aktiiviset lisätyöt aikaväliltä summataan"
          (is (= odotetut-rivit (vec (butlast rivit)))))
        (testing "yhteensä-rivi sisältää vain suodatetut lisätyöt"
          (is (= ["Yhteensä" 102M]
                (get-in (last rivit) [:rivi])))))
      (finally
        (siivoa-testin-lisatyot! tunniste)))))

(deftest MHU21-urakan-tavoitehinnan-oikaisut-muodostuvat-hoitovuosittain
  (let [tv-otsikko-1 "Testioikaisu 2091"
        tv-otsikko-2 "Testioikaisu 2092"
        tv-selite-1 "Ensimmäisen hoitovuoden oikaisu"
        tv-selite-2 "Toisen hoitovuoden oikaisu"
        tv-summa-1 1000
        tv-summa-2 -250
        hoitokausi-1 2091
        hoitokausi-2 2092
        urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        hoitokaudet [{:alkupvm (pvm/luo-pvm-aika hoitokausi-1 9 1 0)
                      :loppupvm (pvm/luo-pvm-aika (inc hoitokausi-1) 8 30 23 59 59)}
                     {:alkupvm (pvm/luo-pvm-aika hoitokausi-2 9 1 0)
                      :loppupvm (pvm/luo-pvm-aika (inc hoitokausi-2) 8 30 23 59 59)}]
        siivoa-testioikaisut! #(u (str "DELETE FROM tavoitehinnan_oikaisu
                                        WHERE \"urakka-id\" = " urakka-id "
                                          AND otsikko IN ('" tv-otsikko-1 "', '" tv-otsikko-2 "')"))]
    (try
      (siivoa-testioikaisut!)
      (u (str "INSERT INTO tavoitehinnan_oikaisu
               (\"urakka-id\", \"muokkaaja-id\", muokattu, otsikko, selite, summa,
                \"hoitokauden-alkuvuosi\", poistettu)
               VALUES (" urakka-id ", " (:id +kayttaja-jvh+) ", NOW(),
                       '" tv-otsikko-1 "', '" tv-selite-1 "', " tv-summa-1 ",
                       " hoitokausi-1 ", false),
                      (" urakka-id ", " (:id +kayttaja-jvh+) ", NOW(),
                       '" tv-otsikko-2 "', '" tv-selite-2 "', " tv-summa-2 ",
                       " hoitokausi-2 ", false)"))
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-tavoitehinnan-oikaisut
                            (:db jarjestelma) urakka-id hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (is (= [(str hoitokausi-1 "-" hoitokausi-2) (bigdec tv-summa-1)]
              (first rivit)))
        (is (= [(str hoitokausi-2 "-" (inc hoitokausi-2)) (bigdec tv-summa-2)]
              (second rivit)))
        (is (= ["Yhteensä" (bigdec (+ tv-summa-1 tv-summa-2))]
              (get-in (last rivit) [:rivi])))
        (is (= "Harjaan kirjatut tavoitehinnan muutokset"
              (get-in taulukko [1 :sheet-nimi]))))
      (finally
        (siivoa-testioikaisut!)))))

(deftest lupaukset-kayttaa-kuukausittaisia-pisteita-toimii
  (let [urakka-id-raasepori (hae-urakan-id-nimella "UUD Raasepori  MHU 2021- 2026, P")
        urakan-tiedot (first (urakat-q/hae-urakka (:db jarjestelma) {:id urakka-id-raasepori}))
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet (:db jarjestelma) urakka-id-raasepori))
        raportti (with-redefs [lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle
                               (fn [_ _] {:lupaus-sitoutuminen {:pisteet 70}
                                          :yhteenveto {:pisteet {:maksimi 100, :ennuste 100, :toteuma 100}}})
                               valikatselmus-q/hae-bonukset (fn [_ _] [{:rahasumma 100M}])
                               valikatselmus-q/hae-sanktiot (fn [_ _] [{:maara -25M}])]
                   (vastaanottotarkastus-mhu/lupaukset-taulukko (:db jarjestelma) urakka-id-raasepori urakan-tiedot hoitokaudet))]
    (is (= ["2021-2022" 70 100 75M]
          (first (last raportti))))))


