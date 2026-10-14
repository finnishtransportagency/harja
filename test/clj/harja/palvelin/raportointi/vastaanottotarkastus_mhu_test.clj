(ns harja.palvelin.raportointi.vastaanottotarkastus-mhu-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]

            [harja.palvelin.komponentit.tietokanta :as tietokanta]

            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat-q]
            [harja.kyselyt.lampotilat :as lampotilat-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-q]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmus-palvelu]
            [harja.palvelin.raportointi.raportit.muutos-ja-lisatyoraportti :as muutos-ja-lisatyoraportti]
            [harja.palvelin.raportointi.raportit :as raportit]
            [harja.palvelin.raportointi.raportit.ymparisto :as ymparisto]
            [harja.palvelin.raportointi.testiapurit :as apurit]
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

(defn etsi-taulukko-avaimella-ja-otsikolla [raportti avain otsikko]
  (some (fn [osa]
          (when (and (vector? osa)
                  (= avain (first osa))
                  (= otsikko (get-in osa [1 :otsikko])))
            osa))
    (tree-seq coll? seq raportti)))

;; Meillä on kaksi vastaantottotarkastusraporttia, joista toinen on päällystysurakoille ja toinen MHU-urakoille.
;; Testataan, että ne ovat rekisteröityinä eri urakkatyyppien alle ja että ne eroavat toisistaan.
(deftest vastaanottotarkastusraportit-ovat-erilliset
  (let [paallystys (get raportit/raportit-nimen-mukaan :vastaanottotarkastusraportti-paallystys)
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

(def ^:private testi-mhu25-urakka-id -1)

(def ^:private testi-mhu25-hoitokaudet
  (mapv (fn [vuosi]
          {:alkupvm (pvm/luo-pvm-aika vuosi 9 1 0)
           :loppupvm (pvm/luo-pvm-aika (inc vuosi) 8 30 23 59 59)})
    (range 2025 2030)))

(def ^:private testi-mhu25-urakan-tiedot
  {:id testi-mhu25-urakka-id
   :nimi "Testi MHU 2025-2030"
   :alkupvm (:alkupvm (first testi-mhu25-hoitokaudet))
   :loppupvm (:loppupvm (last testi-mhu25-hoitokaudet))})

(def ^:private testi-mhu25-urakan-parametrit
  {:muutosten_hallinta true
   :hoitokauden_lopun_kattohinta_kerroin 1.2M})

(def ^:private testi-mhu21-urakka-id -2)

(def ^:private testi-mhu21-hoitokaudet
  (mapv (fn [vuosi]
          {:alkupvm (pvm/luo-pvm-aika vuosi 9 1 0)
           :loppupvm (pvm/luo-pvm-aika (inc vuosi) 8 30 23 59 59)})
    (range 2021 2026)))

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
  (let [urakka-id testi-mhu25-urakka-id
        lupaustiedot (fn [_ {:keys [valittu-hoitokausi]}]
                       (if (= (first valittu-hoitokausi) #inst "2021-10-01T00:00:00.000-00:00")
                         {:lupaus-sitoutuminen {:pisteet 70}
                          :yhteenveto {:valikatselmus-tehty-urakalle? valikatselmus-tehty?
                                       :pisteet {:toteuma 65}}}
                         {:lupaus-sitoutuminen {:pisteet 80}
                          :yhteenveto {:valikatselmus-tehty-urakalle? valikatselmus-tehty?
                                       :pisteet {:toteuma 75}}}))]
    (with-redefs [materiaalit-kyselyt/hae-talvisuolan-kokonaismaara (fn [_ _] [{:kokonaismaara 1000M}])
                  urakat-q/hae-urakka (fn [_ _] [testi-mhu25-urakan-tiedot])
                  urakat-q/hae-urakan-parametrit (fn [_ _] [testi-mhu25-urakan-parametrit])
                  urakat-q/hae-urakan-hoitokaudet (fn [_ _] testi-mhu25-hoitokaudet)
                  urakat-q/hae-yksittainen-urakka (fn [_ _] [testi-mhu25-urakan-tiedot])
                  lampotilat-q/hae-urakan-lampotilat (fn [_ _] [])
                  suolarajoitus-q/hae-talvisuolan-kokonaiskayttoraja (fn [_ _] [{:talvisuolan_kayttoraja 6M}])
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
                  rahavaraus-kyselyt/hae-urakan-perusnimiset-rahavaraukset
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
                       {:id :yhteenveto :summa-indeksikorjattu 420M :toteumat 335M :tavoitehinnan-muutos -85M}]))
                  muutos-ja-lisatyoraportti/hae-kirjallisesti-sovitut-muutokset-raportille (fn [& _] [])
                  muutos-ja-lisatyoraportti/hae-lisatoiden-kulukohdistukset (fn [& _] [])
                  muutos-ja-lisatyoraportti/hae-tavoitehinnan-oikaisut (fn [& _] [])
                  muutos-palvelu/hae-tehtava-maaramuutokset (fn [& _] [])
                  valikatselmus-q/hae-tavoitehintaan-vaikuttavat-arvonvahennykset (fn [& _] [])
                  valikatselmus-palvelu/hae-kustannukset-jarjestettyna (fn [& _] {:taulukon-rivit {}})
                  valikatselmus-palvelu/hae-valikatselmuksen-tiedot-hoitovuodelle (fn [& _] {})
                  vastaanottotarkastus-mhu/hae-sanktiot-vastaanottotarkastusraportille (fn [& _] [])
                  vastaanottotarkastus-mhu/hae-bonukset-vastaanottotarkastusraportille (fn [& _] [])
                  vastaanottotarkastus-mhu/hae-viranomaistehtavamaarat (fn [& _] [])
                  laatupoikkeamat-q/hae-poikkeamaraportilliset-laatupoikkeamat (fn [& _] [])]
      (vastaanottotarkastus-mhu/suorita (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id}))))

(deftest raportti-sisaltaa-lupaukset-hoitovuosittain
  (let [raportti (muodosta-testiraportti true)
        lupaustaulukko (etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko "Lupaukset")]
    (is (= [:taulukko
            {:otsikko "Lupaukset" :sheet-nimi "Lupaukset" :samalle-sheetille? false :tyhja nil :viimeinen-rivi-yhteenveto? true}
            [{:otsikko "Hoitovuosi" :leveys 5}
             {:otsikko "Tarjouksen lupauspisteet" :leveys 5}
             {:otsikko "Toteutuneet lupauspisteet" :leveys 5}
             {:otsikko "Bonus/Sanktiot (€)" :leveys 5 :fmt :raha}]
            [["2025-2026" 80 75 150M]
             ["2026-2027" 80 75 150M]
             ["2027-2028" 80 75 150M]
             ["2028-2029" 80 75 150M]
             ["2029-2030" 80 75 150M]
             {:korosta-hennosti? true
              :lihavoi? true
              :rivi ["Yhteensä"
                     ""
                     ""
                     750M]}]]
          lupaustaulukko))))

(deftest raportti-ei-sisalla-toteutuneita-lupauspisteita-ilman-valikatselmusta
  (let [raportti (muodosta-testiraportti false)
        lupaustaulukko (etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko "Lupaukset")
        lupaus-rivit (nth lupaustaulukko 3)]
    (is (= ["2025-2026" "2026-2027" "2027-2028" "2028-2029" "2029-2030"]
          (mapv #(nth % 0) (take 5 lupaus-rivit))))
    (is (= [nil nil nil nil nil]
          (mapv #(nth % 2) (take 5 lupaus-rivit))))))

(deftest raportti-sisaltaa-talvisuolan-kokonaiskayttomaaran
  (let [raportti (muodosta-testiraportti false)
        yhteenveto-arvot (nth (etsi-taulukko-avaimella-ja-otsikolla raportti :yhteenveto-laatikko "Koko urakka-ajan yhteenveto (kuivatonneina)") 2)
        talvisuolataulukko (etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko "Erittely hoitovuosittain")]
    (is (= {:avain "Tehtävä- ja määräluettelon mukainen käyttöraja", :arvo "30,00 t"} (nth yhteenveto-arvot 0)))
    (is (= {:avain "Kohtuullistettu käyttöraja", :arvo "30,00 t"} (nth yhteenveto-arvot 1)))
    (is (= {:avain "Suurin urakassa sallittu käyttömäärä + 5 %", :arvo "31,50 t"} (nth yhteenveto-arvot 2)))
    (is (= {:avain "Toteuma koko urakka-ajalta", :arvo "5 000,00 t", :lihavoi? true} (nth yhteenveto-arvot 3)))
    (is (= {:avain "josta sallitun käyttömäärän ylittävä, sanktioon johtava toteuma", :arvo "4 968,50 t", :lihavoi? true} (nth yhteenveto-arvot 4)))

    ;; Validoidaan koko taulukko
    (is (= testi-talvisuolan-erittely talvisuolataulukko))))

(deftest raportti-sisaltaa-rahavarausten-tavoitehinnan-muutokset
  (let [raportti (muodosta-testiraportti true)
        taulukko (etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko "Rahavarausten tavoitehintamuutokset")]
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
          taulukko))))

(deftest vastaanottotarkastusraportti-sisaltaa-ymparistoraportin
  (let [urakka-id testi-mhu25-urakka-id
        hoitokaudet testi-mhu25-hoitokaudet
        ymparistoraportin-suorita ymparisto/suorita
        kutsutut-parametrit (atom [])
        taulukoiden-otsikot ["Talvisuolat"
                             "Formiaatit"
                             "Kesäsuola"
                             "Hiekoitushiekka"
                             "Murskeet"
                             "Paikkausmateriaalit"
                             "Muut materiaalit"]]
    (with-redefs [ymparisto/suorita
                  (fn [db kayttaja parametrit]
                    (swap! kutsutut-parametrit conj parametrit)
                    (ymparistoraportin-suorita db kayttaja parametrit))]
      (let [raportti (muodosta-testiraportti true)
            taulukot (mapv #(etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko %)
                       taulukoiden-otsikot)]
        (testing "ympäristöraporttia kutsutaan kerran koko sopimuskaudelle"
          (is (= 1 (count @kutsutut-parametrit)))
          (is (= {:alkupvm (:alkupvm (first hoitokaudet))
                  :loppupvm (:loppupvm (last hoitokaudet))
                  :urakka-id urakka-id
                  :urakoittain? false
                  :urakkatyyppi :teiden-hoito
                  :koko-urakkaaika? true}
                (first @kutsutut-parametrit))))
        (testing "ympäristöraportti sisältää kaikki päätaulukot"
          (is (= taulukoiden-otsikot
                (mapv #(get-in % [1 :otsikko]) taulukot))))
        (testing "ympäristöraportin ensimmäinen taulukko aloittaa oman sheetin"
          (is (false? (get-in (first taulukot) [1 :samalle-sheetille?]))))))))

(deftest MHU25-urakan-tavoitehinnan-muutokset-muodostuvat-kaikille-hoitovuosille
  (let [urakka-id testi-mhu25-urakka-id
        db (:db jarjestelma)
        hoitokaudet testi-mhu25-hoitokaudet
        hoitovuodet (mapv #(pvm/vuosi (:alkupvm %)) hoitokaudet)
        odotetut-rivit [["2025-2026" -3290M]
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
    (testing "testiurakan kaikki hoitovuodet ovat mukana"
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
                      :tavoitehinnan-muutos (get rahavarausten-muutokset hoitokauden-alkuvuosi)}])
                  valikatselmus-q/hae-tavoitehintaan-vaikuttavat-arvonvahennykset
                  (fn [_ {:keys [hoitokauden-alkuvuosi]}]
                    (if (= 2025 hoitokauden-alkuvuosi)
                      [{:maara -3400M}]
                      []))]
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-tavoitehinnan-muutokset
                            db +kayttaja-jvh+ urakka-id hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (testing "jokainen hoitovuosi käyttää oman vuoden kaikkia lähdearvoja"
          (is (= odotetut-rivit (vec (butlast rivit)))))
        (testing "yhteensä-rivi summaa hoitovuosien tulokset"
          (is (= ["Yhteensä" -3446.75M]
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
  (let [urakka-id testi-mhu25-urakka-id
        db (:db jarjestelma)
        hoitokaudet testi-mhu25-hoitokaudet
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
  (let [urakka-id testi-mhu25-urakka-id
        db (:db jarjestelma)
        hoitokaudet testi-mhu25-hoitokaudet
        nimet ["Viranomaistehtävissä avustaminen"
               "Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon"]
        tehtavamaarat {"Viranomaistehtävissä avustaminen"
                       {2025 [{:tuntia 1M}] 2026 [{:tuntia 2M}]}
                       "Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon"
                       {2027 [{:tuntia 30M}] 2028 [{:tuntia 40M}] 2029 [{:tuntia 50M}]}}
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
    (with-redefs [vastaanottotarkastus-mhu/hae-viranomaistehtavamaarat
                  (fn [_ {:keys [nimi hoitovuosi urakka-id]}]
                    (is (= testi-mhu25-urakka-id urakka-id))
                    (is (some #{nimi} nimet))
                    (get-in tehtavamaarat [nimi hoitovuosi] []))]
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
                  (get-in (second excel-taulukot) [1 :excel-alkutekstit])))))))))

(deftest MHU25-urakan-lisatyot-summataan-hoitovuosittain
  (let [urakka-id testi-mhu25-urakka-id
        db (:db jarjestelma)
        hoitokaudet testi-mhu25-hoitokaudet
        hoitovuodet (mapv #(pvm/vuosi (:alkupvm %)) hoitokaudet)
        lisatyot {2025 [{:summa 100M} {:summa 20M}]
                  2026 [{:summa -40M} {:summa 2.5M}]
                  2027 [{:summa 7.25M}]
                  2028 [{:summa 8.25M} {:summa -3.5M}]
                  2029 [{:summa 6M} {:summa 1.5M}]}
        haut (atom [])
        odotetut-rivit [["2025-2026" 120M]
                        ["2026-2027" -37.5M]
                        ["2027-2028" 7.25M]
                        ["2028-2029" 4.75M]
                        ["2029-2030" 7.5M]]]
    (with-redefs [muutos-ja-lisatyoraportti/hae-lisatoiden-kulukohdistukset
                  (fn [_ parametrit]
                    (swap! haut conj parametrit)
                    (get lisatyot (pvm/vuosi (:alkupvm parametrit)) []))]
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-lisatyo-taulukko
                            db urakka-id hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (testing "hoitovuoden lisätyöt summataan oikein"
          (is (= odotetut-rivit (vec (butlast rivit))))
          (is (= ["Yhteensä" 102M]
                (get-in (last rivit) [:rivi]))))
        (testing "haku saa jokaisen vuoden urakka- ja aikarajauksen"
          (is (= (count hoitokaudet) (count @haut)))
          (is (every? #(= urakka-id (:urakka-id %)) @haut))
          (is (= hoitovuodet (mapv #(pvm/vuosi (:alkupvm %)) @haut)))
          (is (= (mapv (juxt :alkupvm :loppupvm) hoitokaudet)
                (mapv (juxt :alkupvm :loppupvm) @haut))))))))

(deftest MHU21-urakan-tavoitehinnan-oikaisut-muodostuvat-hoitovuosittain
  (let [tv-summa-1 1000M
        tv-summa-2 -250M
        tv-summa-3 150M
        tv-summa-4 250M
        tv-summa-5 2500M
        hoitokausi-1 2021
        hoitokausi-2 2022
        hoitokausi-3 2023
        hoitokausi-4 2024
        hoitokausi-5 2025
        urakka-id testi-mhu21-urakka-id
        hoitokaudet testi-mhu21-hoitokaudet
        oikaisut {hoitokausi-1 [{:tavoitehinnan_muutos tv-summa-1}]
                  hoitokausi-2 [{:tavoitehinnan_muutos tv-summa-2}]
                  hoitokausi-3 [{:tavoitehinnan_muutos tv-summa-3}]
                  hoitokausi-4 [{:tavoitehinnan_muutos tv-summa-4}]
                  hoitokausi-5 [{:tavoitehinnan_muutos tv-summa-5}]}
        haetut-hoitovuodet (atom [])]
    (with-redefs [muutos-ja-lisatyoraportti/hae-tavoitehinnan-oikaisut
                  (fn [_ {:keys [urakka-id hoitovuosi]}]
                    (is (= testi-mhu21-urakka-id urakka-id))
                    (swap! haetut-hoitovuodet conj hoitovuosi)
                    (get oikaisut hoitovuosi []))
                  valikatselmus-q/hae-tavoitehintaan-vaikuttavat-arvonvahennykset (fn [& _] [])]
      (let [raportin-osat (vastaanottotarkastus-mhu/muodosta-tavoitehinnan-oikaisut
                            (:db jarjestelma) urakka-id hoitokaudet nil)
            taulukko (first raportin-osat)
            rivit (nth taulukko 3)]
        (is (= [(str hoitokausi-1 "-" hoitokausi-2) (bigdec tv-summa-1)]
              (first rivit)))
        (is (= [(str hoitokausi-2 "-" (inc hoitokausi-2)) (bigdec tv-summa-2)]
              (second rivit)))
        (is (= ["Yhteensä" (bigdec (+ tv-summa-1 tv-summa-2 tv-summa-3 tv-summa-4 tv-summa-5))]
              (get-in (last rivit) [:rivi])))
        (is (= "Harjaan kirjatut tavoitehinnan muutokset"
              (get-in taulukko [1 :sheet-nimi])))
        (is (= [hoitokausi-1 hoitokausi-2 hoitokausi-3 hoitokausi-4 hoitokausi-5] @haetut-hoitovuodet))))))

(deftest lupaukset-kayttaa-kuukausittaisia-pisteita-toimii
  (let [urakka-id -3
        urakan-tiedot {:alkupvm #inst "2019-10-01T00:00:00.000-00:00"}
        hoitokaudet [{:alkupvm #inst "2019-10-01T00:00:00.000-00:00"
                      :loppupvm #inst "2020-09-30T23:59:59.000-00:00"}]
        raportti (with-redefs [lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle
                               (fn [_ _] {:lupaus-sitoutuminen {:pisteet 70}
                                          :yhteenveto {:pisteet {:maksimi 100, :ennuste 100, :toteuma 100}
                                                       :valikatselmus-tehty-urakalle? true}})
                               valikatselmus-q/hae-bonukset (fn [_ _] [{:rahasumma 100M}])
                               valikatselmus-q/hae-sanktiot (fn [_ _] [{:maara -25M}])]
                   (vastaanottotarkastus-mhu/lupaukset-taulukko (:db jarjestelma) urakka-id urakan-tiedot hoitokaudet))]
    (is (= ["2019-2020" 70 100 75M]
          (first (last raportti))))))

(deftest MHU25-urakan-tavoitehintaan-kuuluvat-kustannukset-muodostuvat
  (let [db (:db jarjestelma)
        urakan-tiedot testi-mhu25-urakan-tiedot
        hoitokaudet testi-mhu25-hoitokaudet
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
    (testing "testiurakan kaikki hoitovuodet ovat mukana"
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
                  "Arvonvähennykset (€)"
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
  (let [urakka-id testi-mhu25-urakka-id
        db (:db jarjestelma)
        urakan-tiedot testi-mhu25-urakan-tiedot
        urakan-parametrit testi-mhu25-urakan-parametrit
        hoitokaudet testi-mhu25-hoitokaudet
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
            (vastaanottotarkastus-mhu/muodosta-urakan-tavoitehinnat-taulukko
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
              (first (vastaanottotarkastus-mhu/muodosta-urakan-tavoitehinnat-taulukko
                       db +kayttaja-jvh+ urakan-tiedot urakan-parametrit hoitokaudet :excel))]
          (testing "Excel-raportin otsikko muodostuu"
            (is (= [[:otsikko-title "Urakan lopullinen tavoite- ja kattohinta"]]
                  (get-in excel-taulukko [1 :excel-alkutekstit])))))))))

(deftest sanktiotaulukko-erottelee-muistutukset-poikkeamaraportit-ja-arvonvahennykset
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        hoitokaudet [{:alkupvm #inst "2021-10-01T00:00:00.000-00:00"
                      :loppupvm #inst "2022-09-30T23:59:59.000-00:00"}
                     {:alkupvm #inst "2022-10-01T00:00:00.000-00:00"
                      :loppupvm #inst "2023-09-30T23:59:59.000-00:00"}]
        sanktiot (fn [_ {:keys [alkupvm]}]
                   (if (= alkupvm (-> hoitokaudet first :alkupvm))
                     [{:sakkoryhma "muistutus"}
                      {:sakkoryhma "arvonvahennyssanktio" :maara -20M}
                      {:sakkoryhma "A" :maara -30M}
                      {:maara -5M}]
                     [{:sakkoryhma "muistutus"}
                      {:sakkoryhma "arvonvahennyssanktio" :maara -2M}
                      {:sakkoryhma "A" :maara -3M}]))]
    (with-redefs [vastaanottotarkastus-mhu/hae-sanktiot-vastaanottotarkastusraportille sanktiot
                  laatupoikkeamat-q/hae-poikkeamaraportilliset-laatupoikkeamat
                  (fn [_ {:keys [alku]}]
                    (if (= alku (-> hoitokaudet first :alkupvm))
                      [{} {}]
                      [{}]))]
      (is (= [:taulukko
              {:otsikko "Kirjalliset muistutukset, sanktiot, arvonvähennykset ja poikkeamaraportit"
               :viimeinen-rivi-yhteenveto? true
               :sheet-nimi "Kirjalliset muistutukset, sanktiot, arvonvähennykset ja poikkeamaraportit"
               :excel-alkutekstit nil}
              [{:leveys 5 :otsikko "Hoitovuosi"}
               {:leveys 5 :otsikko "Muistutuksia (kpl)" :fmt :kokonaisluku}
               {:leveys 5 :otsikko "Poikkeamaraportit, jotka eivät johtaneet sakkoihin (kpl)" :fmt :kokonaisluku}
               {:leveys 5 :otsikko "Sanktiot (€)" :fmt :raha}
               {:leveys 5 :otsikko "Arvonvähennykset (€)" :fmt :raha}]
              [["2021-2022" 1 2 -35M -20M]
               ["2022-2023" 1 1 -3M -2M]
               {:lihavoi? true
                :korosta-hennosti? true
                :rivi ["Yhteensä" 2 3 -38M -22M]}]]
            (first (vastaanottotarkastus-mhu/muodosta-sanktiot-taulukko (:db jarjestelma) urakka-id hoitokaudet :html)))))))

(deftest bonustaulukko-erottelee-lupausbonukset-asiastyytyvaisyysbonukset
  (let [urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        hoitokaudet [{:alkupvm #inst "2021-10-01T00:00:00.000-00:00"
                      :loppupvm #inst "2022-09-30T23:59:59.000-00:00"}
                     {:alkupvm #inst "2022-10-01T00:00:00.000-00:00"
                      :loppupvm #inst "2023-09-30T23:59:59.000-00:00"}]
        bonukset (fn [_ {:keys [alkupvm]}]
                   (if (= alkupvm (-> hoitokaudet first :alkupvm))
                     [{:tyyppi "lupausbonus" :rahasumma 20M}
                      {:tyyppi "asiakastyytyvaisyysbonus" :rahasumma 30M}]
                     [{:tyyppi "lupausbonus" :rahasumma 2M}
                      {:tyyppi "asiakastyytyvaisyysbonus" :rahasumma 3M}]))]
    (with-redefs [vastaanottotarkastus-mhu/hae-bonukset-vastaanottotarkastusraportille bonukset]
      (is (= [:taulukko
              {:otsikko "Bonukset"
               :viimeinen-rivi-yhteenveto? true
               :sheet-nimi "Bonukset"
               :excel-alkutekstit nil}
              [{:leveys 5 :otsikko "Hoitovuosi"}
               {:leveys 5 :otsikko "Lupausbonus (€)" :fmt :raha}
               {:leveys 5 :otsikko "Asiakastyytyväisyysbonus (€)" :fmt :raha}
               {:leveys 5 :otsikko "Muut bonukset (€)" :fmt :raha}
               {:leveys 5 :otsikko "Yhteensä (€)" :fmt :raha}]
              [["2021-2022" 20M 30M 0 50M]
               ["2022-2023" 2M 3M 0 5M]
               {:lihavoi? true
                :korosta-hennosti? true
                :rivi ["Yhteensä" 22M 33M 0 55M]}]]
            (first (vastaanottotarkastus-mhu/muodosta-bonukset-taulukko (:db jarjestelma) urakka-id hoitokaudet :html)))))))

;; Tämä testi käyttää tietokannasta löytyviä kovakoodattuja arvoja. Testit failaa, jos uutta testidataa tulee.
(deftest raportin-tiedot-tietokannasta-toimii
  (let [db (:db jarjestelma)
        kajaanin-urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        oulu19-urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        iin-urakka-id (hae-iin-maanteiden-hoitourakan-2021-2026-id)
        kajaanin-raportti (vastaanottotarkastus-mhu/suorita db +kayttaja-jvh+ {:urakka-id kajaanin-urakka-id})
        oulu19-raportti (vastaanottotarkastus-mhu/suorita db +kayttaja-jvh+ {:urakka-id oulu19-urakka-id})
        iin-raportti (vastaanottotarkastus-mhu/suorita db +kayttaja-jvh+ {:urakka-id iin-urakka-id})
        hae-taulukko (fn [raportti otsikko]
                       (etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko otsikko))
        vuosirivit (fn [taulukko]
                     (when taulukko
                       (vec (butlast (nth taulukko 3)))))
        yhteensarivi (fn [taulukko]
                       (when taulukko
                         (last (nth taulukko 3))))
        vuosilabelit (fn [taulukko]
                       (mapv first (vuosirivit taulukko)))]
    (testing "Ympäristöraportti Oulu19 urakalle"
      (let [;; Käytetään vanhaa urakkaa, koska sillä on dataa olemassa
            hoitokaudet (sort-by :alkupvm (urakat-q/hae-urakan-hoitokaudet db oulu19-urakka-id))
            vuodet (mapv #(pvm/vuosi (:alkupvm %)) hoitokaudet)
            alkupvm (:alkupvm (first hoitokaudet))
            loppupvm (:loppupvm (last hoitokaudet))
            ;; Haetaan tietokannasta kaikki raportin tiedot, jotta voidaan verrata raportin sisältöä tietokannan sisältöön.
            tietokantarivit (ymparisto/hae-raportti db alkupvm loppupvm oulu19-urakka-id nil :teiden-hoito false)
            tietokanta-suolarivit (->> tietokantarivit
                                    (filter #(= "talvisuola" (get-in (first %) [:materiaali :tyyppi])))
                                    (mapcat second))
            tietokanta-toteumat (filter #(and (= "toteuma" (:maarantyyppi %))
                                           (nil? (:talvitieluokka %))
                                           (nil? (:soratieluokka %)))
                                  tietokanta-suolarivit)
            tietokanta-suunnitelmat (filter #(and (= "suunnitelma" (:maarantyyppi %))
                                               (nil? (:talvitieluokka %))
                                               (nil? (:soratieluokka %)))
                                      (mapcat second tietokantarivit))
            tietokanta-hoitoluokkatoteumat (filter #(and (= "toteuma" (:maarantyyppi %))
                                                      (:talvitieluokka %))
                                             tietokanta-suolarivit)
            tietokanta-suunnitelman-summa (fn [tyyppi]
                                 (reduce + 0 (map :maara (filter #(= tyyppi (get-in % [:materiaali :tyyppi])) tietokanta-suunnitelmat))))
            tietokanta-summat-vuosittain (into {}
                                (map (fn [[vuosi rivit]]
                                       [vuosi (reduce + (map :maara rivit))]))
                                (group-by :hoitokauden-alkuvuosi tietokanta-toteumat))
            tietokanta-hoitoluokkien-summat-vuosittain (into {}
                                              (map (fn [[vuosi rivit]]
                                                     [vuosi (reduce + (map :maara rivit))]))
                                              (group-by :hoitokauden-alkuvuosi tietokanta-hoitoluokkatoteumat))

            raportti (ymparisto/suorita db nil {:alkupvm alkupvm
                                                :loppupvm loppupvm
                                                :urakka-id oulu19-urakka-id
                                                :urakoittain? false
                                                :urakkatyyppi :teiden-hoito
                                                :koko-urakkaaika? true})
            talvisuolataulukko (etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko "Talvisuolat")
            formiaattitaulukko (etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko "Formiaatit")
            mursketaulukko (etsi-taulukko-avaimella-ja-otsikolla raportti :taulukko "Murskeet")
            talvisuola-sarakevuodet (mapv :otsikko (take (count vuodet) (drop 2 (nth talvisuolataulukko 2))))
            talvisuola-datarivit (nth talvisuolataulukko 3)
            riviotsikko (fn [rivi]
                          (apurit/raporttisolun-arvo (second (:rivi rivi))))
            suunniteltu-summa (fn [taulukko nimi]
                                (let [rivi (some #(when (= nimi (riviotsikko %)) %) (nth taulukko 3))
                                      summa (apurit/raporttisolun-arvo (nth (:rivi rivi) (+ 3 (count vuodet))))]
                                  ;; Taulukon arvot on nil - jos arvoa ei ole, mutta pyöristetään se nollaksi.
                                  (or summa 0)))
            yhteenvetorivi (some #(when (= "Talvisuolat yhteensä" (riviotsikko %)) %) talvisuola-datarivit)
            talvisuola-hoitoluokkarivit (filter #(and (:isanta-rivin-id %)
                                                   (not= "Poikkeama (+/-)" (riviotsikko %)))
                                          talvisuola-datarivit)
            raportoidut-hoitoluokkien-summat
            (reduce (fn [summat rivi]
                      (reduce (fn [summat [indeksi vuosi]]
                                (let [maara (apurit/raporttisolun-arvo (nth (:rivi rivi) (+ 2 indeksi)))]
                                  (if (number? maara)
                                    (update summat vuosi (fnil + 0) maara)
                                    summat)))
                        summat
                        (map-indexed vector vuodet)))
              {}
              talvisuola-hoitoluokkarivit)]
        (is (= (mapv #(str % "-" (inc %)) vuodet) talvisuola-sarakevuodet))
        (is (seq tietokanta-hoitoluokkatoteumat) "Testiurakalla on hoitoluokittaisia talvisuolatoteumia tietokantahaussa")
        (is (some? yhteenvetorivi) "Talvisuolojen yhteenvetorivi löytyy ympäristöraportista")
        (is (= (mapv #(get tietokanta-summat-vuosittain % "–") vuodet)
              (mapv #(apurit/raporttisolun-arvo
                       (nth (:rivi yhteenvetorivi) (+ 2 %)))
                (range (count vuodet)))))
        (is (= (tietokanta-suunnitelman-summa "formiaatti")
              (suunniteltu-summa formiaattitaulukko "Formiaatit raportista täsmää formiaatteihin tietokannasta")))
        (is (= (tietokanta-suunnitelman-summa "murske")
              (suunniteltu-summa mursketaulukko "Murskeet raportista täsmää murskeisiin tietokannasta")))
        (is (= (select-keys tietokanta-hoitoluokkien-summat-vuosittain vuodet)
              (select-keys raportoidut-hoitoluokkien-summat vuodet)) "Raportin hoitoluokkien summat vastaavat tietokantahakua")))

    (testing "Lupaukset taulukko"
      (let [taulukko (hae-taulukko kajaanin-raportti "Lupaukset")]
        (is (some? taulukko))
        (is (= ["2025-2026" "2026-2027" "2027-2028" "2028-2029" "2029-2030"]
              (vuosilabelit taulukko)))
        ;; Kovakoodattu "Tarjouksen lupauspisteet", joka voi hajota, jos testidataan kosketaan
        (is (= 80 (second (first (vuosirivit taulukko)))))
        ;; Kovakoodattu "Toteutuneet lupauspisteet", joka voi hajota, jos testidataan kosketaan
        (is (= nil (nth (first (vuosirivit taulukko)) 2)))
        ;; Kovakoodattu "Bonus/Sanktiot (€)", joka voi hajota, jos testidataan kosketaan
        (is (= 1150M (nth (first (vuosirivit taulukko)) 3)))))

    (testing "Rahavarausten tavoitehintamuutokset taulukko"
      (let [taulukko (hae-taulukko iin-raportti "Rahavarausten tavoitehintamuutokset")
            vuoden-2025-rivi (some #(when (= "2025-2026" (first %)) %) (vuosirivit taulukko))]
        (is (some? taulukko))
        (is (= ["2021-2022" "2022-2023" "2023-2024" "2024-2025" "2025-2026"]
              (vuosilabelit taulukko)))
        (is (some pos? (rest vuoden-2025-rivi)))
        (is (= 133200M (second vuoden-2025-rivi))) ;; Kovakoodattu "Suunniteltu määrä (€)", joka voi hajota, jos testidataan kosketaan
        (is (= 100000M (nth vuoden-2025-rivi 2))) ;; Kovakoodattu "Toteutunut määrä (€)", joka voi hajota, jos testidataan kosketaan
        (is (= 2640M (nth vuoden-2025-rivi 3)))
        (is (= 1000M (nth vuoden-2025-rivi 4)))
        (is (= 39600M (nth vuoden-2025-rivi 5)))
        (is (= 0 (nth vuoden-2025-rivi 6)))
        (is (= -74440M (nth vuoden-2025-rivi 7)))))

    (testing "Vuonna 2025 alkavan urakan tavoitehinnan muutokset taulukko"
      (let [taulukko (hae-taulukko kajaanin-raportti "Harjaan kirjatut tavoitehinnan muutokset")]
        (is (some? taulukko))
        (is (= ["2025-2026" "2026-2027" "2027-2028" "2028-2029" "2029-2030"]
              (vuosilabelit taulukko)))
        (is (every? number? (map second (vuosirivit taulukko))))
        (is (= -34960.0 (second (first (vuosirivit taulukko)))))
        (is (= -35526.0 (second (second (vuosirivit taulukko)))))
        (is (= -6143.2 (second (nth (vuosirivit taulukko) 2))))
        (is (= -9978.0 (second (nth (vuosirivit taulukko) 3))))
        (is (= 0.0 (second (nth (vuosirivit taulukko) 4))))
        (is (= -86607.2 (second (:rivi (yhteensarivi taulukko)))))))

    (testing "Vuonna 2021 alkavan urakan tavoitehinnan oikaisut taulukko"
      (let [taulukko (hae-taulukko iin-raportti "Harjaan kirjatut tavoitehinnan muutokset")
            rivit (vuosirivit taulukko)]
        (is (some? taulukko))
        (is (= ["2021-2022" "2022-2023" "2023-2024" "2024-2025" "2025-2026"]
              (vuosilabelit taulukko)))
        (is (= 31234M (second (first rivit))))))

    (testing "Lisätyöt-taulukko Kajaani25"
      (let [taulukko (hae-taulukko kajaanin-raportti "Lisätyöt")]
        (is (some? taulukko))
        (is (= ["2025-2026" "2026-2027" "2027-2028" "2028-2029" "2029-2030"]
              (vuosilabelit taulukko)))
        (is (every? number? (map second (vuosirivit taulukko))))))

    (testing "Lisätyöt-taulukko Oulu19"
      (let [taulukko (hae-taulukko oulu19-raportti "Lisätyöt")]
        (is (some? taulukko))
        (is (= ["2019-2020" "2020-2021" "2021-2022" "2022-2023" "2023-2024"]
              (vuosilabelit taulukko)))
        (is (every? number? (map second (vuosirivit taulukko))))
        (is (= 5004.85M (second (first (vuosirivit taulukko)))))
        (is (= 0 (second (second (vuosirivit taulukko)))))
        (is (= 5004.85M (second (:rivi (yhteensarivi taulukko)))))))

    (testing "Sanktiot-taulukko"
      (let [taulukko (hae-taulukko kajaanin-raportti
                       "Kirjalliset muistutukset, sanktiot, arvonvähennykset ja poikkeamaraportit")
            vuoden-2025-rivi (first (vuosirivit taulukko))]
        (is (some? taulukko))
        (is (= "2025-2026" (first vuoden-2025-rivi)))
        (is (= 0 (second vuoden-2025-rivi)))
        (is (= 0 (nth vuoden-2025-rivi 2)))
        (is (= -1850M (nth vuoden-2025-rivi 3)))
        (is (= -3400M (nth vuoden-2025-rivi 4)))

        ;; Yhteensä rivi
        (is (= 0 (second (:rivi (yhteensarivi taulukko)))))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 2)))
        (is (= -1850M (nth (:rivi (yhteensarivi taulukko)) 3)))
        (is (= -3400M (nth (:rivi (yhteensarivi taulukko)) 4)))))

    (testing "Bonukset-taulukko"
      (let [taulukko (hae-taulukko kajaanin-raportti "Bonukset")
            vuoden-2025-rivi (first (vuosirivit taulukko))]
        (is (some? taulukko))
        (is (= "2025-2026" (first vuoden-2025-rivi)))
        (is (= 500M (second vuoden-2025-rivi)))
        (is (= 2500M (nth vuoden-2025-rivi 2)))
        (is (= 0 (nth vuoden-2025-rivi 3)))
        (is (= 3000M (nth vuoden-2025-rivi 4)))

        ;; Yhteensä rivi
        (is (= 500M (second (:rivi (yhteensarivi taulukko)))))
        (is (= 2500M (nth (:rivi (yhteensarivi taulukko)) 2)))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 3)))
        (is (= 3000M (nth (:rivi (yhteensarivi taulukko)) 4)))))

    (testing "Viranomaistehtävät-taulukko kajaani25"
      (let [taulukko (hae-taulukko kajaanin-raportti "Viranomaistehtävät")]
        (is (some? taulukko))
        (is (= ["2025-2026" "2026-2027" "2027-2028" "2028-2029" "2029-2030"]
              (vuosilabelit taulukko)))
        (is (every? number? (map second (vuosirivit taulukko))))))

    (testing "Viranomaistehtävät-taulukko iin-urakka"
      (let [taulukko (hae-taulukko iin-raportti "Viranomaistehtävät")]
        (is (some? taulukko))
        (is (= ["2021-2022" "2022-2023" "2023-2024" "2024-2025" "2025-2026"]
              (vuosilabelit taulukko)))
        (is (= 9M (second (second (vuosirivit taulukko)))))
        (is (= 9M (second (:rivi (yhteensarivi taulukko)))))))

    (testing "Tavoitehintaan kuuluvat kustannukset -taulukko"
      (let [taulukko (hae-taulukko kajaanin-raportti "Urakan tavoitehintaan kuuluvat kustannukset")]
        (is (some? taulukko))
        (is (= ["2025-2026" "2026-2027" "2027-2028" "2028-2029" "2029-2030"]
              (vuosilabelit taulukko)))
        (is (= "2025-2026" (first (first (vuosirivit taulukko)))))
        (is (= 632M (second (first (vuosirivit taulukko)))))
        (is (= 0 (nth (first (vuosirivit taulukko)) 2)))
        (is (= 0 (nth (first (vuosirivit taulukko)) 3)))
        (is (= 0 (nth (first (vuosirivit taulukko)) 4)))
        (is (= -3400M (nth (first (vuosirivit taulukko)) 5)))
        (is (= 0 (nth (first (vuosirivit taulukko)) 6)))
        (is (= -2768M (nth (first (vuosirivit taulukko)) 7)))

        ;; Yhteensä rivi
        (is (= 27550M (second (:rivi (yhteensarivi taulukko)))))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 2)))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 3)))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 4)))
        (is (= -3400M (nth (:rivi (yhteensarivi taulukko)) 5)))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 6)))
        (is (= 24150M (nth (:rivi (yhteensarivi taulukko)) 7)))))

    (testing "Lopullinen tavoitehinta -taulukko"
      (let [taulukko (hae-taulukko kajaanin-raportti "Urakan lopullinen tavoite- ja kattohinta")]
        (is (some? taulukko))
        (is (= ["2025-2026" "2026-2027" "2027-2028" "2028-2029" "2029-2030"]
              (vuosilabelit taulukko)))

        (is (= "2025-2026" (first (first (vuosirivit taulukko)))))
        (is (= 2079712.022942 (second (first (vuosirivit taulukko)))))
        (is (= 2495654.4275304 (nth (first (vuosirivit taulukko)) 2)))
        (is (= 0 (nth (first (vuosirivit taulukko)) 3)))
        (is (= 0 (nth (first (vuosirivit taulukko)) 4)))
        (is (= 0 (nth (first (vuosirivit taulukko)) 5)))
        (is (= 0 (nth (first (vuosirivit taulukko)) 6)))
        (is (= 4575366.4504724 (nth (first (vuosirivit taulukko)) 7)))

        ;; Yhteensä rivi
        (is (= 4247314.299442 (second (:rivi (yhteensarivi taulukko)))))
        (is (= 4943674.2539304 (nth (:rivi (yhteensarivi taulukko)) 2)))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 3)))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 4)))
        (is (= 19345.44 (nth (:rivi (yhteensarivi taulukko)) 5)))
        (is (= 0 (nth (:rivi (yhteensarivi taulukko)) 6)))
        (is (= 9210333.9933724 (nth (:rivi (yhteensarivi taulukko)) 7)))))))

