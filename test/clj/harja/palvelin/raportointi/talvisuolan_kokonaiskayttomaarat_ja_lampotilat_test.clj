(ns harja.palvelin.raportointi.talvisuolan-kokonaiskayttomaarat-ja-lampotilat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.pvm :as pvm]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit
             [tietokanta :as tietokanta]
             [pdf-vienti :as pdf-vienti]]
            [clj-time
             [core :as t]]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.raportit.talvihoitosuolan-kokonaiskayttomaara :as talvisuola-rap]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :pdf-vienti (component/using
                        (pdf-vienti/luo-pdf-vienti)
                        [:http-palvelin])
          :raportointi (component/using
                         (raportointi/luo-raportointi)
                         [:db :pdf-vienti])
          :raportit (component/using
                      (raportit/->Raportit)
                      [:http-palvelin :db :raportointi :pdf-vienti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))
;; Helpperit
(defn tarkista-sarakkeet [taulukko]
  (apurit/tarkista-taulukko-sarakkeet
    taulukko
    {:otsikko "Hoitovuosi", :leveys 1, :fmt :kokonaisluku, :tasaa :vasen}
    {:otsikko "Keskilämpötilojen keskiarvo tarkastelujaksolla (°C)", :leveys 1, :fmt :numero, :tasaa :oikea}
    {:otsikko "Keskilämpötilojen keskiarvo pitkällä aikavälillä (°C)", :leveys 1, :fmt :numero, :tasaa :oikea}
    {:otsikko "Erotus (°C)", :leveys 1, :fmt :numero, :tasaa :oikea}
    {:otsikko "Lämpötilan vaikutus käyttörajaan", :leveys 1, :fmt :teksti, :tasaa :oikea}
    {:otsikko "Käyttöraja (kuivatonnia)", :leveys 1, :fmt :numero, :tasaa :oikea}
    {:otsikko "Kohtuullistettu käyttöraja (kuivatonnia)", :leveys 1, :fmt :numero, :tasaa :oikea}
    {:otsikko "Toteuma (kuivatonnia)", :leveys 1, :fmt :numero, :tasaa :oikea}
    {:otsikko "Erotus (kuivatonnia)", :leveys 1, :fmt :numero, :tasaa :oikea}))

(def lampotiladata '({:id 1906, :alkupvm #inst "2021-09-30T21:00:00.000-00:00", :loppupvm #inst "2022-09-29T21:00:00.000-00:00", :keskilampotila -9.20M, :keskilampotila-1991-2020 nil, :keskilampotila-1981-2010 -9.90M, :keskilampotila-1971-2000 nil}
                     {:id 2008, :alkupvm #inst "2022-09-30T21:00:00.000-00:00", :loppupvm #inst "2023-09-29T21:00:00.000-00:00", :keskilampotila -6.30M, :keskilampotila-1991-2020 -8.80M, :keskilampotila-1981-2010 -9.90M, :keskilampotila-1971-2000 nil}
                     {:id 2106, :alkupvm #inst "2023-09-30T21:00:00.000-00:00", :loppupvm #inst "2024-09-29T21:00:00.000-00:00", :keskilampotila -11.20M, :keskilampotila-1991-2020 -8.80M, :keskilampotila-1981-2010 -9.90M, :keskilampotila-1971-2000 nil}
                     {:id 2305, :alkupvm #inst "2024-09-30T21:00:00.000-00:00", :loppupvm #inst "2025-09-29T21:00:00.000-00:00", :keskilampotila -6.80M, :keskilampotila-1991-2020 -8.80M, :keskilampotila-1981-2010 -9.90M, :keskilampotila-1971-2000 -8.80M}))

;; Testit

(deftest paattele-raportin-viimeinen-hoitovuosi-test
  (with-redefs [t/now #(t/date-time 2023 8 15 12)]
    ;; Tyhjän saa pyytämättäkin
    (is (= nil nil))
    (is (= 1900 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi (pvm/luo-pvm-dec-kk 1900 9 30) lampotiladata)))
    ;; Nykyhetkeksi on määritelty 2023 elokuu, joten hoitokausi on kesken, ja oikeaa päätösvuotta ei voida käyttää
    (is (= 2023 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi (pvm/luo-pvm-dec-kk 2023 9 30) lampotiladata)))
    ;; Nykyhetkeksi on määritelty 3023 elokuu, joten hoitokausi on kesken, ja oikeaa päätösvuotta ei voida käyttää
    ;; Vaikka se olisi kuinka myöhään tulevaisuudessa
    (is (= (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))
          (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi (pvm/luo-pvm-dec-kk 3023 9 30) lampotiladata)))))

(deftest paattele-raportin-viimeinen-hoitovuosi-2-test
  (testing "Urakka on päättynyt"
    (with-redefs [t/now #(t/date-time 2023 8 15 12)]
      (is (= 1900 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi
                    (pvm/luo-pvm-dec-kk 1900 9 30)
                    [])))
      (is (= 2022 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi
                    (pvm/luo-pvm-dec-kk 2022 9 30)
                    [])))))

  (testing "Hoitokausi valmis - ollaan lokakuussa tai myöhemmin"
    (with-redefs [pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2025 10 15))]
      (is (= 2025 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi
                    (pvm/luo-pvm-dec-kk 2025 9 30)
                    lampotiladata))))
    (with-redefs [pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2023 8 15))]
      (is (= 2023 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi
                    (pvm/luo-pvm-dec-kk 2025 9 30)
                    lampotiladata))))
    (with-redefs [pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2023 3 15))]
      (is (= 2023 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi
                    (pvm/luo-pvm-dec-kk 2025 9 30)
                    lampotiladata))))
    (with-redefs [pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2023 11 15))]
      (is (= 2023 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi
                    (pvm/luo-pvm-dec-kk 2025 9 30)
                    lampotiladata))))
    (with-redefs [pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2023 12 31))]
      (is (= 2023 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi
                    (pvm/luo-pvm-dec-kk 2025 9 30)
                    lampotiladata)))))

  (testing "Tammi-elokuu, edellisen vuoden lämpötiladata löytyy"
    (let [lampotilat [{:alkupvm #inst "2022-09-30T21:00:00.000-00:00" :loppupvm #inst "2023-09-29T21:00:00.000-00:00"}
                      {:alkupvm #inst "2023-09-30T21:00:00.000-00:00" :loppupvm #inst "2024-09-29T21:00:00.000-00:00"}]]
      (with-redefs [pvm/nyt (constantly (pvm/luo-pvm-dec-kk 2023 1 15))]
        (is (= 2022 (talvisuola-rap/paattele-raportin-viimeinen-hoitovuosi
                      (pvm/luo-pvm-dec-kk 2024 9 30)
                      lampotilat)))))))


(deftest kohtuullistettu-kayttoraja-test
  (is (= 1.0 (talvisuola-rap/kohtuullistettu-kayttoraja 1 0)))
  (is (= 1.1 (talvisuola-rap/kohtuullistettu-kayttoraja 1 10)))
  (is (= 1.2 (talvisuola-rap/kohtuullistettu-kayttoraja 1 20)))
  (is (= 1.3 (talvisuola-rap/kohtuullistettu-kayttoraja 1 30)))
  ;; Arvojen on oltava 0 - 30 välissä
  (is (= nil (talvisuola-rap/kohtuullistettu-kayttoraja 1 40)))
  (is (= nil (talvisuola-rap/kohtuullistettu-kayttoraja 1 -1))))

(deftest lampotilan-vaikutus-suolan-kulutukseen-test
  (is (= 0 (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen -1)))
  (is (= 0 (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen 0)))
  (is (= 0 (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen 1)))
  (is (= 10 (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen 2)))
  (is (= 20 (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen 3)))
  (is (= 30 (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen 4)))
  (is (= 30 (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen 5)))
  (is (= 30 (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen 6)))
  (is (= nil (talvisuola-rap/lampotilan-vaikutus-suolan-kulutukseen nil))))

(deftest jasenna-raportin-otsikko-test
  (let [urakan-tiedot {:nimi "Oulu MHU 2019-2024"
                       :alkupvm (pvm/luo-pvm-dec-kk 2022 10 1)
                       :loppupvm (pvm/luo-pvm-dec-kk 2023 9 30)}
        hoitovuodet '(2021)]
    (is (= "Talvihoitosuolan kokonaiskäyttömäärä ja lämpötilatarkastelu 01.10.2022 - 30.09.2022"
          (talvisuola-rap/jasenna-raportin-otsikko urakan-tiedot hoitovuodet)))))

(deftest paattele-kaytettava-keskilampotilajakso-test
  (let [lampotila-vuodelle {:keskilampotila-1971-2000 1 ;; ennen vuotta 2014
                            :keskilampotila-1981-2010 2 ;; ennen vuotta 2022
                            :keskilampotila-1991-2020 3} ;; muut
        ]
    ;; Jos ei ole tiedossa, niin palautetaan nil
    (is (= nil (talvisuola-rap/paattele-kaytettava-keskilampotilajakso nil lampotila-vuodelle)))
    (is (= 1 (talvisuola-rap/paattele-kaytettava-keskilampotilajakso 2012 lampotila-vuodelle)))
    (is (= 1 (talvisuola-rap/paattele-kaytettava-keskilampotilajakso 2014 lampotila-vuodelle)))
    (is (= 2 (talvisuola-rap/paattele-kaytettava-keskilampotilajakso 2015 lampotila-vuodelle)))
    (is (= 2 (talvisuola-rap/paattele-kaytettava-keskilampotilajakso 2016 lampotila-vuodelle)))
    (is (= 2 (talvisuola-rap/paattele-kaytettava-keskilampotilajakso 2022 lampotila-vuodelle)))
    (is (= 3 (talvisuola-rap/paattele-kaytettava-keskilampotilajakso 2023 lampotila-vuodelle)))
    (is (= 3 (talvisuola-rap/paattele-kaytettava-keskilampotilajakso 2027 lampotila-vuodelle)))
    (is (= nil (talvisuola-rap/paattele-kaytettava-keskilampotilajakso "jarkko" lampotila-vuodelle)))))

(deftest raportin-suoritus-talvisuolan-kokonaismaaralle-toimii
  (let [urakan-nimi "Oulun MHU 2019-2024"
        urakka-id (hae-urakan-id-nimella urakan-nimi)
        urakan-tiedot (first (q-map (str "select alkupvm, loppupvm from urakka where id = " urakka-id ";")))
        kuluva-tai-viimeinen-hoitokausi (min 2024 (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt)))))
        hoitovuodet (range 2019 kuluva-tai-viimeinen-hoitokausi)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :talvisuolanlämpötilaraportti
                   :konteksti "urakka"
                   :urakka-id urakka-id})
        otsikko (talvisuola-rap/jasenna-raportin-otsikko urakan-tiedot hoitovuodet)]
    (is (vector? vastaus))
    (let [taulukko (apurit/taulukko-otsikolla vastaus otsikko)]

      (tarkista-sarakkeet taulukko)

      (is (= vastaus
            [:raportti {:nimi "Oulun MHU 2019-2024"
                        :orientaatio :landscape
                        :rajoita-pdf-rivimaara nil}
             [:taulukko {:otsikko (str "Talvihoitosuolan kokonaiskäyttömäärä ja lämpötilatarkastelu 01.10.2019 - 30.09." (inc (apply max hoitovuodet))), :tyhja nil, :sheet-nimi "Talvihoitosuolat"}
              [{:otsikko "Hoitovuosi", :leveys 1, :fmt :kokonaisluku, :tasaa :vasen}
               {:otsikko "Keskilämpötilojen keskiarvo tarkastelujaksolla (°C)", :leveys 1, :fmt :numero, :tasaa :oikea}
               {:otsikko "Keskilämpötilojen keskiarvo pitkällä aikavälillä (°C)", :leveys 1, :fmt :numero, :tasaa :oikea} {:otsikko "Erotus (°C)", :leveys 1, :fmt :numero, :tasaa :oikea}
               {:otsikko "Lämpötilan vaikutus käyttörajaan", :leveys 1, :fmt :teksti, :tasaa :oikea}
               {:otsikko "Käyttöraja (kuivatonnia)", :leveys 1, :fmt :numero, :tasaa :oikea}
               {:otsikko "Kohtuullistettu käyttöraja (kuivatonnia)", :leveys 1, :fmt :numero, :tasaa :oikea}
               {:otsikko "Toteuma (kuivatonnia)", :leveys 1, :fmt :numero, :tasaa :oikea}
               {:otsikko "Erotus (kuivatonnia)", :leveys 1, :fmt :numero, :tasaa :oikea}]
              (concat
                [[[:arvo {:arvo "2019-2020"}] -3.50M -5.60M 2.10M "+10 %" [:arvo {:arvo "Käyttöraja puuttuu", :huomio? true}] [:arvo {:arvo "-"}] 1300M
                  [:arvo {:arvo 0, :jos-tyhja "-", :desimaalien-maara 2, :ryhmitelty? true, :korosta-hennosti? true}]]]

                (mapv
                  (fn [vuosi]
                    [[:arvo {:arvo (str vuosi "-" (inc vuosi))}] [:arvo {:arvo "Lämpötilatieto puuttuu", :huomio? true}] [:arvo {:arvo "Lämpötilatieto puuttuu", :huomio? true}] 0 "0 %" [:arvo {:arvo "Käyttöraja puuttuu", :huomio? true}] [:arvo {:arvo "-"}] [:arvo {:arvo "-"}] [:arvo {:arvo 0, :jos-tyhja "-", :desimaalien-maara 2, :ryhmitelty? true, :korosta-hennosti? true}]])
                  (rest hoitovuodet))

                [{:lihavoi? true, :korosta-hennosti? true,
                  :rivi [[:arvo {:arvo "Yhteensä"}] nil nil nil nil
                         [:arvo
                          {:arvo nil
                           :jos-tyhja "-"}]
                         [:arvo
                          {:arvo nil
                           :jos-tyhja "-"}]
                         1300M
                         [:arvo
                          {:arvo 0
                           :desimaalien-maara 2
                           :jos-tyhja "-"
                           :korosta-hennosti? true
                           :ryhmitelty? true}]]}])]])))))
