(ns harja.palvelin.raportointi.ymparistoraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.urakat :as urk-q]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.testiapurit :as apurit]))

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

(defn tarkistusfunktio [sisalto]
  (let [rivi (:rivi sisalto)
        materiaali (first rivi)
        [yhteensa prosentti suunniteltu] (take-last 3 rivi)
        hoitokaudet (drop-last 3 (rest rivi))
        solu? #(or (nil? %)
                   (and (apurit/raporttisolu? %) (number? (apurit/raporttisolun-arvo %)))
                   (apurit/tyhja-raporttisolu? %))]
    (and (= (count rivi) 16)
         (string? materiaali)
         (every? solu? hoitokaudet)
         (solu? yhteensa)
         (solu? suunniteltu)
         (or (nil? prosentti) (and (number? prosentti) (or (= 0 prosentti) (pos? prosentti))))
         (or (and (every? nil? hoitokaudet) (nil? yhteensa))
             (= (reduce (fnil + 0 0) (map apurit/raporttisolun-arvo hoitokaudet))
                (apurit/raporttisolun-arvo yhteensa)))
         (or
           (nil? prosentti) (nil? yhteensa) (nil? suunniteltu)
           (= (/ (* 100.0 (apurit/raporttisolun-arvo yhteensa)) (apurit/raporttisolun-arvo suunniteltu))
              prosentti)))))


(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :ymparistoraportti
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 9 30))}})
        talvisuolojen-kaytto (nth (butlast vastaus) 2)
        talvisuolojen-maxmaara (nth (butlast vastaus) 3)
        talvisuolojen-toteumaprosentti (nth (butlast vastaus) 4)]
    (is (vector? vastaus))
    (let [otsikko "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= talvisuolojen-kaytto [:teksti "Erilaisia talvisuoloja käytetty valitulla aikavälillä: 0,00t"]) "talvisuolan toteutunut määrä")
      (is (= talvisuolojen-maxmaara [:teksti "Hoitokauden talvisuolan maksimimäärä urakassa: 800t"]) "talvisuolan max-määrä")
      (is (= talvisuolojen-toteumaprosentti [:teksti "Toteumaprosentti suhteessa hoitokauden maksimimäärään: 0,0%"]) "talvisuola tot-%")
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Materiaali"}
                                          {:otsikko "10/15"}
                                          {:otsikko "11/15"}
                                          {:otsikko "12/15"}
                                          {:otsikko "01/16"}
                                          {:otsikko "02/16"}
                                          {:otsikko "03/16"}
                                          {:otsikko "04/16"}
                                          {:otsikko "05/16"}
                                          {:otsikko "06/16"}
                                          {:otsikko "07/16"}
                                          {:otsikko "08/16"}
                                          {:otsikko "09/16"}
                                          {:otsikko "Määrä yhteensä"}
                                          {:otsikko "Tot-%"}
                                          {:otsikko "Suunniteltu määrä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :ymparistoraportti
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm      (c/to-date (t/local-date 2015 10 1))
                                                      :loppupvm     (c/to-date (t/local-date 2016 9 30))
                                                      :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (let [otsikko "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Materiaali"}
                                          {:otsikko "10/15"}
                                          {:otsikko "11/15"}
                                          {:otsikko "12/15"}
                                          {:otsikko "01/16"}
                                          {:otsikko "02/16"}
                                          {:otsikko "03/16"}
                                          {:otsikko "04/16"}
                                          {:otsikko "05/16"}
                                          {:otsikko "06/16"}
                                          {:otsikko "07/16"}
                                          {:otsikko "08/16"}
                                          {:otsikko "09/16"}
                                          {:otsikko "Määrä yhteensä"}
                                          {:otsikko "Tot-%"}
                                          {:otsikko "Suunniteltu määrä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :ymparistoraportti
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm     (c/to-date (t/local-date 2016 9 30))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (let [otsikko "KOKO MAA, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Materiaali"}
                                          {:otsikko "10/15"}
                                          {:otsikko "11/15"}
                                          {:otsikko "12/15"}
                                          {:otsikko "01/16"}
                                          {:otsikko "02/16"}
                                          {:otsikko "03/16"}
                                          {:otsikko "04/16"}
                                          {:otsikko "05/16"}
                                          {:otsikko "06/16"}
                                          {:otsikko "07/16"}
                                          {:otsikko "08/16"}
                                          {:otsikko "09/16"}
                                          {:otsikko "Määrä yhteensä"}
                                          {:otsikko "Tot-%"}
                                          {:otsikko "Suunniteltu määrä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest ymparisto-materiaali-ja-suolaraportin-tulokset-tasmaavat
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        param {:alkupvm (c/to-date (t/local-date 2014 10 1))
               :loppupvm (c/to-date (t/local-date 2015 9 30))
               :urakkatyyppi "hoito"}
        ymparisto (apurit/taulukko-otsikolla
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :suorita-raportti
                                    +kayttaja-jvh+
                                    {:nimi :ymparistoraportti
                                     :konteksti "urakka"
                                     :urakka-id urakka-id
                                     :parametrit param})
                    "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.10.2014 - 30.09.2015")
        ymp-kaytetty-suola (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 5 2))
        ymp-suola-yht (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 13 2))
        ymp-hiekka-totpros (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 14 9))
        ymp-hiekka-suunniteltu (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 15 9))
        materiaali (apurit/taulukko-otsikolla
                     (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :suorita-raportti
                                     +kayttaja-jvh+
                                     {:nimi :materiaaliraportti
                                      :konteksti "urakka"
                                      :urakka-id urakka-id
                                      :parametrit param})
                     "Oulun alueurakka 2014-2019, Materiaaliraportti ajalta 01.10.2014 - 30.09.2015")
        mat-kaytetty-suola (apurit/taulukon-solu materiaali 1 0)
        mat-kaytetty-hiekka (apurit/taulukon-solu materiaali 2 0)
        suola (apurit/taulukko-otsikolla
                (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :suolasakko
                                 :konteksti  "urakka"
                                 :urakka-id urakka-id
                                 :parametrit param})
                "Oulun alueurakka 2014-2019, Suolasakkoraportti ajalta 01.10.2014 - 30.09.2015")
        suola-kaytetty-suola (apurit/taulukon-solu suola 8 0)]
    (is (= ymp-kaytetty-suola mat-kaytetty-suola suola-kaytetty-suola)
        "Ympäristö-, suola- ja materiaaliraportin pitäisi laskea käytetyn suolan summa samalla tavalla")
    (is (= ymp-kaytetty-suola ymp-suola-yht) "Ympäristöraportin käytetyn ja yhteenlasketun suolan määrä pitäisi olla sama")
    ;; Testidatasta riippuvia testejä.. vähän huonoja
    (is (= 0.0 ymp-hiekka-totpros) "Ympäristöraportin hiekan toteumaprosentin pitäisi olla nolla, toteumia ei ole")
    (is (= 0 mat-kaytetty-hiekka) "Materiaaliraportin pitäisi raportoida hiekan määräksi nolla, koska toteumia ei ole")
    (is (= 800M ymp-hiekka-suunniteltu) "Onko testidata muuttunut? Ympäristöraportti odottaa, että hiekoitushiekkaa on suunniteltu 800t")))

(deftest jokainen-materiaali-vain-kerran
  (let [taulukko (apurit/taulukko-otsikolla
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :suorita-raportti
                                   +kayttaja-jvh+
                                   {:nimi       :ymparistoraportti
                                    :konteksti  "urakka"
                                    :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                    :parametrit {:alkupvm  (c/to-date (t/local-date 2014 10 1))
                                                 :loppupvm (c/to-date (t/local-date 2015 9 30))}})
                   "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.10.2014 - 30.09.2015")
        nimet (apurit/taulukon-sarake taulukko 0)]
    (is (= (count nimet) (count (into #{} nimet))) "Materiaalien nimet ovat ympäristöraportissa vain kerran.")))