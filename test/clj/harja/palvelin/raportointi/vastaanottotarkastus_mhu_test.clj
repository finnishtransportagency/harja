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
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmus-palvelu]
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
  [:taulukko {:otsikko "Erittely hoitovuosittain" :samalle-sheetille? true :sheet-nimi "Talvihoitosuolat" :tyhja nil}
   [{:fmt :kokonaisluku :leveys 1 :otsikko "Hoitovuosi" :tasaa :vasen}
    {:fmt :numero :leveys 1 :otsikko "Keskilämpötilojen keskiarvo tarkastelujaksolla (°C)" :tasaa :oikea}
    {:fmt :numero :leveys 1 :otsikko "Keskilämpötilojen keskiarvo pitkällä aikavälillä (°C)" :tasaa :oikea}
    {:fmt :numero :leveys 1 :otsikko "Erotus (°C)" :tasaa :oikea}
    {:fmt :teksti :leveys 1 :otsikko "Lämpötilan vaikutus käyttörajaan" :tasaa :oikea}
    {:fmt :numero :leveys 1 :otsikko "Käyttöraja tehtävä- ja määräluettelossa (kuivatonnia)" :tasaa :oikea}
    {:fmt :numero :leveys 1 :otsikko "Kohtuullistettu käyttöraja (kuivatonnia)" :tasaa :oikea}
    {:fmt :numero :leveys 1 :otsikko "Toteuma (kuivatonnia)" :tasaa :oikea}]
   [[[:arvo {:arvo "2025-2026"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "-"}]
     "-"
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 1000M :desimaalien-maara 2}]]
    [[:arvo {:arvo "2026-2027"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "-"}]
     "-"
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 1000M :desimaalien-maara 2}]]
    [[:arvo {:arvo "2027-2028"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "-"}]
     "-"
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 1000M :desimaalien-maara 2}]]
    [[:arvo {:arvo "2028-2029"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "-"}]
     "-"
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 1000M :desimaalien-maara 2}]]
    [[:arvo {:arvo "2029-2030"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "Ei vielä saatavilla"}]
     [:arvo {:arvo "-"}]
     "-"
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 6M :desimaalien-maara 2}]
     [:arvo {:arvo 1000M :desimaalien-maara 2}]]
    {:korosta-hennosti? true
     :lihavoi? true
     :rivi [[:arvo {:arvo "Yhteensä"}] nil nil nil nil
            [:arvo {:arvo 30M :desimaalien-maara 2}]
            [:arvo {:arvo 30M :desimaalien-maara 2}]
            [:arvo {:arvo 5000M :desimaalien-maara 2}]]}]])

(defn- muodosta-testiraportti [valikatselmus-tehty?]
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        lupaustiedot (fn [_ {:keys [valittu-hoitokausi]}]
                       (if (= (first valittu-hoitokausi) #inst "2021-10-01T00:00:00.000-00:00")
                         {:lupaus-sitoutuminen {:pisteet 70}
                          :yhteenveto {:valikatselmus-tehty-urakalle? valikatselmus-tehty?
                                       :pisteet {:toteuma 65}}}
                         {:lupaus-sitoutuminen {:pisteet 80}
                          :yhteenveto {:valikatselmus-tehty-urakalle? valikatselmus-tehty?
                                       :pisteet {:toteuma 75}}}))]
    (with-redefs [materiaalit-kyselyt/hae-talvisuolan-kokonaismaara (fn [_ _] [{:kokonaismaara 1000M}])
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
                     {:id 3 :nimi "Tilaajan rahavaraus kannustinjärjestelmään"}
                     {:id 4 :nimi "Muu rahavaraus 1"}
                     {:id 5 :nimi "Muu rahavaraus 2"}])
                  rahavaraus-kyselyt/muutosten-rahavaraukset
                  (fn [_ _ hoitokauden-alkuvuosi]
                    (if (= hoitokauden-alkuvuosi 2021)
                      [{:id 1 :summa-indeksikorjattu 100M :toteumat 80M :tavoitehinnan-muutos -20M}
                       {:id 2 :summa-indeksikorjattu 50M :toteumat 40M :tavoitehinnan-muutos -10M}
                       {:id 3 :summa-indeksikorjattu 25M :toteumat 20M :tavoitehinnan-muutos -5M}
                       {:id 4 :summa-indeksikorjattu 10M :toteumat 8M :tavoitehinnan-muutos -2M}
                       {:id 5 :summa-indeksikorjattu 20M :toteumat 15M :tavoitehinnan-muutos -5M}
                       {:id :yhteenveto :summa-indeksikorjattu 205M :toteumat 163M :tavoitehinnan-muutos -42M}]
                      [{:id 1 :summa-indeksikorjattu 200M :toteumat 150M :tavoitehinnan-muutos -50M}
                       {:id 2 :summa-indeksikorjattu 100M :toteumat 90M :tavoitehinnan-muutos -10M}
                       {:id 3 :summa-indeksikorjattu 50M :toteumat 45M :tavoitehinnan-muutos -5M}
                       {:id 4 :summa-indeksikorjattu 30M :toteumat 20M :tavoitehinnan-muutos -10M}
                       {:id 5 :summa-indeksikorjattu 40M :toteumat 30M :tavoitehinnan-muutos -10M}
                       {:id :yhteenveto :summa-indeksikorjattu 420M :toteumat 335M :tavoitehinnan-muutos -85M}]))]
      (vastaanottotarkastus-mhu/suorita (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id}))))

(deftest raportti-sisaltaa-lupaukset-hoitovuosittain
  (let [raportti (muodosta-testiraportti true)]
    (is (= [:taulukko
            {:otsikko "Lupaukset" :sheet-nimi "Lupaukset" :samalle-sheetille? false :tyhja nil}
            [{:otsikko "Hoitovuosi" :leveys 5}
             {:otsikko "Tarjouksen lupauspisteet" :leveys 5}
             {:otsikko "Toteutuneet lupauspisteet" :leveys 5}
             {:otsikko "Bonus/Sanktiot (€)" :leveys 5 :fmt :raha}]
            [["2025-2026" 80 75 150M]
             ["2026-2027" 80 75 150M]
             ["2027-2028" 80 75 150M]
             ["2028-2029" 80 75 150M]
             ["2029-2030" 80 75 150M]]]
          (nth raportti 2)))))

(deftest raportti-ei-sisalla-toteutuneita-lupauspisteita-ilman-valikatselmusta
  (let [raportti (muodosta-testiraportti false)
        lupaus-taulukko (nth raportti 2)
        lupaus-rivit (nth lupaus-taulukko 3)]
    (is (= [nil nil nil nil nil]
          (mapv #(nth % 2) lupaus-rivit)))))

(deftest raportti-sisaltaa-talvisuolan-kokonaiskayttomaaran
  (let [raportti (muodosta-testiraportti false)
        yhteenveto-arvot (nth (some (fn [osa]
                                      (when (and (vector? osa)
                                              (= :yhteenveto-laatikko (first osa))
                                              (= "Koko urakka-ajan yhteenveto (kuivatonneina)" (get-in osa [1 :otsikko])))
                                        osa))
                                (tree-seq coll? seq raportti)) 2)]
    (is (= {:avain "Tehtävä- ja määräluettelon mukainen käyttöraja", :arvo "30,00 t"} (nth yhteenveto-arvot 0)))
    (is (= {:avain "Kohtuullistettu käyttöraja", :arvo "30,00 t"} (nth yhteenveto-arvot 1)))
    (is (= {:avain "Suurin urakassa sallittu käyttömäärä + 5 %", :arvo "31,50 t"} (nth yhteenveto-arvot 2)))
    (is (= {:avain "Toteuma koko urakka-ajalta", :arvo "5 000,00 t", :lihavoi? true} (nth yhteenveto-arvot 3)))
    (is (= {:avain "josta sallitun käyttömäärän ylittävä, sanktioon johtava toteuma", :arvo "4 968,50 t", :lihavoi? true} (nth yhteenveto-arvot 4)))

    ;; Validoidaan koko taulukko
    (is (= testi-talvisuolan-erittely (nth raportti 6)))))

(deftest raportti-sisaltaa-rahavarausten-tavoitehinnan-muutokset
  (let [raportti (muodosta-testiraportti true)]
    (is (= [:taulukko
            {:otsikko "Rahavarausten tavoitehintamuutokset"
             :sheet-nimi "Rahavarausten tavoitehintamuutokset"
             :tyhja nil
             :viimeinen-rivi-yhteenveto? true
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
                          {:teksti "Muut tilaajan rahavaraukset"
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
             {:otsikko "Suunniteltu määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Toteutunut määrä (€)" :leveys 5 :fmt :raha}
             {:otsikko "Tavoitehinnan muutos (€)" :leveys 5 :fmt :raha}]
            [["2025-2026" 200M 150M 100M 90M 50M 45M 70M 50M -85M]
             ["2026-2027" 200M 150M 100M 90M 50M 45M 70M 50M -85M]
             ["2027-2028" 200M 150M 100M 90M 50M 45M 70M 50M -85M]
             ["2028-2029" 200M 150M 100M 90M 50M 45M 70M 50M -85M]
             ["2029-2030" 200M 150M 100M 90M 50M 45M 70M 50M -85M]
             {:lihavoi? true
              :korosta-hennosti? true
              :rivi ["Yhteensä" 1000M 750M 500M 450M 250M 225M 350M 250M -425M]}]]
          (nth raportti 8)))
    (is (not-any? #(and (vector? %) (= "Ympäristöraportti" (get-in % [1 :otsikko]))) raportti))))

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

(deftest MHU25-urakan-viranomaistehtavat-muodostuvat-hoitovuosittain
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        sopimus-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-sopimus-id)
        db (:db jarjestelma)
        ;; Tehtävän nimi on vaihtunut kesken kaiken, niin käytetään niitä molempia
        hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        vanha-tehtava-id (ffirst (q "SELECT id FROM tehtava WHERE nimi = 'Viranomaistehtävissä avustaminen'"))
        uusi-tehtava-id (ffirst (q "SELECT id FROM tehtava WHERE nimi = 'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon'"))
        tunniste "vastaanottotarkastus-mhu-viranomaistehtavat-test"
        toteumat [[vanha-tehtava-id 2025 "2025-10-15 12:00:00" 1M]
                  [vanha-tehtava-id 2026 "2026-10-15 12:00:00" 2M]
                  [uusi-tehtava-id 2027 "2027-10-15 12:00:00" 30M]
                  [uusi-tehtava-id 2028 "2028-10-15 12:00:00" 40M]
                  [uusi-tehtava-id 2029 "2029-10-15 12:00:00" 50M]]
        odotetut-vanhan-tehtavan-rivit [["2025-2026" 1M]
                                        ["2026-2027" 2M]
                                        ["2027-2028" 0]
                                        ["2028-2029" 0]
                                        ["2029-2030" 0]]
        odotetut-uuden-tehtavan-rivit [["2025-2026" 0]
                                       ["2026-2027" 0]
                                       ["2027-2028" 30M]
                                       ["2028-2029" 40M]
                                       ["2029-2030" 50M]]]
    (try
      (u (format "DELETE FROM toteuma_tehtava
                   WHERE toteuma IN (SELECT id FROM toteuma WHERE lisatieto LIKE '%s%%')"
           tunniste))
      (u (format "DELETE FROM toteuma WHERE lisatieto LIKE '%s%%'" tunniste))
      (doseq [[tehtava-id hoitovuosi alkanut maara] toteumat]
        (let [lisatieto (str tunniste "-" tehtava-id "-" alkanut)]
          (i (format "INSERT INTO toteuma
                       (luoja, lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, lisatieto)
                       VALUES (%s, 'harja-ui'::lahde, %s, %s, NOW(), '%s', '%s',
                               'kokonaishintainen'::toteumatyyppi, '%s')"
               (:id +kayttaja-jvh+) urakka-id sopimus-id alkanut alkanut lisatieto))
          (i (format "INSERT INTO toteuma_tehtava
                       (luoja, toteuma, luotu, toimenpidekoodi, maara, urakka_id, lisatieto, hoitokauden_alkuvuosi)
                       VALUES (%s, (SELECT id FROM toteuma WHERE lisatieto = '%s'), NOW(), %s, %s, %s, '%s', %s)"
               (:id +kayttaja-jvh+) lisatieto tehtava-id maara urakka-id lisatieto hoitovuosi))))
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-virhanomaistehtavat-taulukko
                            db urakka-id hoitokaudet nil)
            vanhan-tehtavan-taulukko (first raportin-osat)
            uuden-tehtavan-taulukko (second raportin-osat)
            vanhan-tehtavan-rivit (nth vanhan-tehtavan-taulukko 3)
            uuden-tehtavan-rivit (nth uuden-tehtavan-taulukko 3)]
        (testing "Vanha ja uusi tehtävä muodostavat omat taulukkonsa"
          (is (= 2 (count raportin-osat))))
        (testing "Vanhan tehtävän hoitovuodet summataan erikseen"
          (is (= odotetut-vanhan-tehtavan-rivit (vec (butlast vanhan-tehtavan-rivit)))))
        (testing "Vanhan tehtävän yhteensä-rivi summataan oikein"
          (is (= ["Yhteensä" 3M]
                (get-in (last vanhan-tehtavan-rivit) [:rivi]))))
        (testing "Uuden tehtävän hoitovuodet summataan erikseen"
          (is (= odotetut-uuden-tehtavan-rivit (vec (butlast uuden-tehtavan-rivit)))))
        (testing "Uuden tehtävän yhteensä-rivi summataan oikein"
          (is (= ["Yhteensä" 120M] (get-in (last uuden-tehtavan-rivit) [:rivi]))))
        (testing "vanhan tehtävän taulukon otsikko on oikein"
          (is (= [{:leveys 5 :otsikko "Hoitovuosi"}
                  {:leveys 5 :otsikko "Viranomaistehtävissä avustaminen (h)" :fmt :kokonaisluku}]
                (nth vanhan-tehtavan-taulukko 2))))
        (testing "Uuden tehtävän taulukon otsikot on oikein"
          (is (= [{:leveys 5 :otsikko "Hoitovuosi"}
                  {:leveys 5 :otsikko "Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon (h)"
                   :fmt :kokonaisluku}]
                (nth uuden-tehtavan-taulukko 2))))
        (testing "Taulukoiden metatiedot ovat oikein"
          (is (every? #(= "Viranomaistehtävät" (get-in % [1 :otsikko])) raportin-osat))
          (is (every? #(= "Viranomaistehtävät" (get-in % [1 :sheet-nimi])) raportin-osat))
          (is (every? #(true? (get-in % [1 :viimeinen-rivi-yhteenveto?])) raportin-osat)))
        (let [excel-taulukko (first (vastaanottotarkastus-mhu/muodosta-virhanomaistehtavat-taulukko
                                      db urakka-id hoitokaudet :excel))]
          (testing "Vanhan tehtävän Excel-otsikko muodostuu"
            (is (= [[:otsikko-title "Viranomaistehtävissä avustaminen"]]
                  (get-in excel-taulukko [1 :excel-alkutekstit])))))
        (let [excel-taulukot (vastaanottotarkastus-mhu/muodosta-virhanomaistehtavat-taulukko
                               db urakka-id hoitokaudet :excel)]
          (testing "Uuden tehtävän Excel-otsikko muodostuu"
            (is (= [[:otsikko-title "Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon"]]
                  (get-in (second excel-taulukot) [1 :excel-alkutekstit]))))))
      ;; Siivotaan lisätyt toteumat
      (finally
        (u (format "DELETE FROM toteuma_tehtava
                     WHERE toteuma IN (SELECT id FROM toteuma WHERE lisatieto LIKE '%s%%')"
             tunniste))
        (u (format "DELETE FROM toteuma WHERE lisatieto LIKE '%s%%'" tunniste))))))

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
           :lisatyon-lisatieto (str "Kajaani lisätyö " vuosi)})
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
                                          :yhteenveto {:pisteet {:maksimi 100, :ennuste 100, :toteuma 100}
                                                       :valikatselmus-tehty-urakalle? true}})
                               valikatselmus-q/hae-bonukset (fn [_ _] [{:rahasumma 100M}])
                               valikatselmus-q/hae-sanktiot (fn [_ _] [{:maara -25M}])]
                   (vastaanottotarkastus-mhu/lupaukset-taulukko (:db jarjestelma) urakka-id-raasepori urakan-tiedot hoitokaudet))]
    (is (= ["2021-2022" 70 100 75M]
          (first (last raportti))))))

(deftest MHU25-urakan-tavoitehintaan-kuuluvat-kustannukset-muodostuvat
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        db (:db jarjestelma)
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        hoitokaudet (sort-by :alkupvm
                      (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        kustannukset
        {2025 {:hankintakustannukset-toteutunut 100M
               :rahavaraukset-toteutunut 10M
               :arvonvahennykset-toteutunut -3M
               :muukulu-tavoitehintainen-toteutunut 2.5M
               :erillishankinnat-toteutunut 20M
               :johto-ja-hallintokorvaus-toteutunut 30M
               :hoidonjohdonpalkkio-toteutunut 40M}
         2026 {:hankintakustannukset-toteutunut 200M
               :rahavaraukset-toteutunut 20M
               :arvonvahennykset-toteutunut -5M
               :muukulu-tavoitehintainen-toteutunut 4M
               :erillishankinnat-toteutunut 21M
               :johto-ja-hallintokorvaus-toteutunut 31M
               :hoidonjohdonpalkkio-toteutunut 41M}
         2027 {:hankintakustannukset-toteutunut 300M
               :rahavaraukset-toteutunut 30M
               :arvonvahennykset-toteutunut -7M
               :muukulu-tavoitehintainen-toteutunut 5M
               :erillishankinnat-toteutunut 22M
               :johto-ja-hallintokorvaus-toteutunut 32M
               :hoidonjohdonpalkkio-toteutunut 42M}
         2028 {:hankintakustannukset-toteutunut 400M
               :rahavaraukset-toteutunut 40M
               :arvonvahennykset-toteutunut -9M
               :muukulu-tavoitehintainen-toteutunut 6M
               :erillishankinnat-toteutunut 23M
               :johto-ja-hallintokorvaus-toteutunut 33M
               :hoidonjohdonpalkkio-toteutunut 43M}
         2029 {:hankintakustannukset-toteutunut 500M
               :rahavaraukset-toteutunut 50M
               :arvonvahennykset-toteutunut -11M
               :muukulu-tavoitehintainen-toteutunut 7M
               :erillishankinnat-toteutunut 24M
               :johto-ja-hallintokorvaus-toteutunut 34M
               :hoidonjohdonpalkkio-toteutunut 44M}}
        odotetut-rivit
        [["2025-2026" 110M 20M 30M 40M -3M 2.5M 199.5M]
         ["2026-2027" 220M 21M 31M 41M -5M 4M 312M]
         ["2027-2028" 330M 22M 32M 42M -7M 5M 424M]
         ["2028-2029" 440M 23M 33M 43M -9M 6M 536M]
         ["2029-2030" 550M 24M 34M 44M -11M 7M 648M]]
        odotettu-yhteensa ["Yhteensä" 1650M 110M 160M 210M -35M 24.5M 2119.5M]]
    (testing "Kajaanin urakan kaikki hoitovuodet ovat mukana"
      (is (= [2025 2026 2027 2028 2029]
            (mapv #(pvm/vuosi (:alkupvm %)) hoitokaudet))))
    (with-redefs [valikatselmus-palvelu/hae-kustannukset-jarjestettyna
                  (fn [_ _ hoitovuosi _ _]
                    {:taulukon-rivit (get kustannukset hoitovuosi)})]
      (let [raportin-osat
            (vastaanottotarkastus-mhu/muodosta-tavoitehintaan-kuuluvat-kustannukset-taulukko
              db urakan-tiedot hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (testing "hankintakustannukset sisältävät rahavaraukset, arvonvähennykset ja muut kulut"
          (is (= odotetut-rivit
                (vec (butlast rivit)))))
        (testing "yhteensä-rivi summaa kaikki kustannussarakkeet oikein"
          (is (= odotettu-yhteensa
                (get-in (last rivit) [:rivi]))))
        (testing "taulukon otsikot ovat oikein"
          (is (= ["Hoitovuosi"
                  "Hankintakustannukset sis.rahavaraukset (€)"
                  "Erillishankinnat (€)"
                  "Johto- ja hallintokorvaus (€)"
                  "Hoidonjohtopalkkio (€)"
                  "Arvonvahennykset (€)"
                  "Muut kulut (€)"
                  "Yhteensä (€)"]
                (mapv :otsikko (nth taulukko 2)))))
        (testing "taulukon metatiedot ovat oikein"
          (is (= "Urakan tavoitehintaan kuuluvat kustannukset"
                (get-in taulukko [1 :otsikko])))
          (is (= "Urakan tavoitehintaan kuuluvat kustannukset"
                (get-in taulukko [1 :sheet-nimi])))
          (is (true? (get-in taulukko [1 :viimeinen-rivi-yhteenveto?]))))))))

(deftest MHU25-urakan-lopullinen-tavoite-ja-kattohinta-muodostuvat
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        db (:db jarjestelma)
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit db urakka-id))
        hoitokaudet (sort-by :alkupvm
                      (urakat-q/hae-urakan-hoitokaudet db urakka-id))
        hoitovuodet (mapv #(pvm/vuosi (:alkupvm %)) hoitokaudet)
        hoitovuoden-tiedot
        {2025 {:yhteenveto {:budjettitavoite {:hoitovuoden-lopun-tavoitehinta 1000M
                                              :hoitovuoden-lopun-kattohinta 1200M
                                              :kirjallisesti-sovitut-muutokset 100M
                                              :yhteenveto {:kustannukset-yhteensa
                                                           {:yht-toteutunut-summa 1500M}}}
                            :toteumiin-perustuvat-muutokset-yht 50M
                            :tavoitehintaan-vaikuttavat-arvonvahennykset [{:maara 10M}]}
               :paatokset [{:hoitovuoden-lopun-indeksikorjaus
                            {:id 1 :hoitokauden_lopun_indeksikorjaus 20M}}
                           {:tavoitehinnan-ylitys {:id 1 :urakoitsija_maksaa 30M}}]}
         2026 {:yhteenveto {:budjettitavoite {:hoitovuoden-lopun-tavoitehinta 2000M
                                              :hoitovuoden-lopun-kattohinta 2400M
                                              :kirjallisesti-sovitut-muutokset 200M
                                              :yhteenveto {:kustannukset-yhteensa
                                                           {:yht-toteutunut-summa 3000M}}}
                            :toteumiin-perustuvat-muutokset-yht 100M
                            :tavoitehintaan-vaikuttavat-arvonvahennykset [{:maara -20M}]}
               :paatokset [{:hoitovuoden-lopun-indeksikorjaus {:id 1 :hoitokauden_lopun_indeksikorjaus 99M}}
                           {:kattohinnan-ylitys {:id 2 :ylityksen_maara 50M :urakoitsija_maksaa 25M}}]}
         2027 {:yhteenveto {:budjettitavoite {:hoitovuoden-lopun-tavoitehinta 3000M
                                              :hoitovuoden-lopun-kattohinta 3600M
                                              :kirjallisesti-sovitut-muutokset 0M
                                              :yhteenveto {:kustannukset-yhteensa {:yht-toteutunut-summa 3300M}}}
                            :toteumiin-perustuvat-muutokset-yht 0M
                            :tavoitehintaan-vaikuttavat-arvonvahennykset []}
               :paatokset [{:tavoitehinnan-alitus {:id 1 :tavoitepalkkio 40M}}]}
         2028 {:yhteenveto {:budjettitavoite {:hoitovuoden-lopun-tavoitehinta 4000M
                                              :hoitovuoden-lopun-kattohinta 4800M
                                              :kirjallisesti-sovitut-muutokset 100M
                                              :yhteenveto {:kustannukset-yhteensa {:yht-toteutunut-summa 4900M}}}
                            :toteumiin-perustuvat-muutokset-yht 0M
                            :tavoitehintaan-vaikuttavat-arvonvahennykset [{:maara 50M}]}
               :paatokset []}
         2029 {:yhteenveto {:budjettitavoite {:hoitovuoden-lopun-tavoitehinta 5000M
                                              :hoitovuoden-lopun-kattohinta 6000M
                                              :kirjallisesti-sovitut-muutokset -50M
                                              :yhteenveto {:kustannukset-yhteensa
                                                           {:yht-toteutunut-summa 7000M}}}
                            :toteumiin-perustuvat-muutokset-yht 0M
                            :tavoitehintaan-vaikuttavat-arvonvahennykset []}
               :paatokset []}}
        haut (atom [])]
    (is (= [2025 2026 2027 2028 2029] hoitovuodet))
    (is (true? (:muutosten_hallinta urakan-parametrit)))
    (is (= 1.2M (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)))
    (with-redefs [valikatselmus-palvelu/hae-valikatselmuksen-tiedot-hoitovuodelle
                  (fn [_ _ parametrit]
                    (swap! haut conj parametrit)
                    (get hoitovuoden-tiedot (:hoitovuosi parametrit)))]
      (let [raportin-osat
            (vastaanottotarkastus-mhu/muodosta-urakan-tavoitehinat-taulukko
              db +kayttaja-jvh+ urakan-tiedot urakan-parametrit hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (testing "jokaisen hoitovuoden tavoite- ja kattohinta lasketaan oikein"
          (is (= [["2025-2026" 1160M 1392.00M 0 30M 108.00M 0 2690.00M]
                  ["2026-2027" 2280M 2736.00M 0 0 50M 25M 5091.00M]
                  ["2027-2028" 3000M 3600.00M 40M 0 0 0 6640.00M]
                  ["2028-2029" 4150M 4980.00M 0 0 0 0 9130.00M]
                  ["2029-2030" 4950M 5940.00M 0 0 1060.00M 0 11950.00M]]
                (vec (butlast rivit)))))
        (testing "yhteensä-rivi summaa kaikki sarakkeet oikein"
          (is (= ["Yhteensä" 15540M 18648.00M 40M 30M 1218.00M 25M 35501.00M]
                (get-in (last rivit) [:rivi]))))
        (testing "taulukon otsikot ja metatiedot ovat oikein"
          (is (= ["Hoitovuosi"
                  "Hoitovuoden lopun tavoitehinta (€)"
                  "Hoitovuoden lopun kattohinta (€)"
                  "Urakoitsijan tavoitepalkkio (€)"
                  "Urakoitsija maksaa tavoitehinnan ylityksestä (€)"
                  "Kattohinnan ylitys (€)"
                  "Urakoitsija maksaa kattohinnan ylityksestä (€)"]
                (mapv :otsikko (nth taulukko 2))))
          (is (= "Urakan lopullinen tavoite- ja kattohinta"
                (get-in taulukko [1 :otsikko])))
          (is (= "Urakan lopullinen tavoite- ja kattohinta"
                (get-in taulukko [1 :sheet-nimi])))
          (is (true? (get-in taulukko [1 :viimeinen-rivi-yhteenveto?])))
          (is (nil? (get-in taulukko [1 :excel-alkutekstit]))))
        (testing "haku tehdään kerran jokaista hoitovuotta ja oikeaa urakkaa kohden"
          (is (= hoitovuodet (mapv :hoitovuosi @haut)))
          (is (every? #(= urakka-id (:urakkaid %)) @haut)))
        (let [excel-taulukko
              (first (vastaanottotarkastus-mhu/muodosta-urakan-tavoitehinat-taulukko
                       db +kayttaja-jvh+ urakan-tiedot urakan-parametrit hoitokaudet :excel))]
          (testing "Excel-raportin otsikko muodostuu"
            (is (= [[:otsikko-title "Urakan lopullinen tavoite- ja kattohinta"]]
                  (get-in excel-taulukko [1 :excel-alkutekstit])))))))))
