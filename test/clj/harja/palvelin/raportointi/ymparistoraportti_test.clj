(ns harja.palvelin.raportointi.ymparistoraportti-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.kyselyt
             [urakat :as urk-q]
             [raportit :as raportit-q]]
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
  (raportit-q/paivita_raportti_cachet (:db jarjestelma))
  (async/<!! (async/go-loop
               [k 1]
               (let [materiaali-cache-ajettu? (ffirst (q "SELECT exists(SELECT 1 FROM raportti_toteutuneet_materiaalit)"))]
                 (when (and (not materiaali-cache-ajettu?)
                            (< k 10))
                   (async/<! (async/timeout 1000))
                   (recur (inc k))))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      urakkatieto-fixture
                      jarjestelma-fixture))

(defn tarkistusfunktio [sisalto]
  (let [rivi (:rivi sisalto)
        materiaali (first rivi)
        [yhteensa prosentti suunniteltu] (take-last 3 rivi)
        hoitokaudet (drop-last 3 (rest rivi))
        solu? #(or (nil? %)
                   (and (apurit/raporttisolu? %) (number? (apurit/raporttisolun-arvo %)))
                   (apurit/tyhja-raporttisolu? %))]

    (or
      ; hoitoluokittainen materiaalitieto
      (and (some #(= (str " - " (:nimi %)) (first sisalto))
                 hoitoluokat/talvihoitoluokat))
      ; väliotsikko
      (and
        (contains? sisalto :otsikko)
        (or (= "Talvisuolat" (:otsikko sisalto))
            (= "Muut materiaalit" (:otsikko sisalto))))
      ; datarivi
      (and (= (count rivi) 16)
          (string? materiaali)
          (every? solu? hoitokaudet)
          (solu? yhteensa)
          (solu? suunniteltu)
          (or (nil? prosentti)
              (and (number? prosentti) (or (= 0 prosentti) (pos? prosentti)))
              (and (vector? prosentti) (= "%" (:yksikko (second prosentti))))) ;; Tämä on näitä keissejä varten [:arvo-ja-yksikko {:arvo 277.625, :yksikko "%", :desimaalien-maara 2}]
          (or (and (every? nil? hoitokaudet) (nil? yhteensa))
              (= (reduce (fnil + 0 0) (map apurit/raporttisolun-arvo hoitokaudet))
                 (apurit/raporttisolun-arvo yhteensa)))
          (or
            (nil? prosentti) (nil? yhteensa) (nil? suunniteltu)
            (= (/ (* 100.0 (apurit/raporttisolun-arvo yhteensa)) (apurit/raporttisolun-arvo suunniteltu))
               (if (number? prosentti)
                 prosentti
                 (apurit/raporttisolun-arvo prosentti))))))))


(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :ymparistoraportti
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 9 30))}})
        talvisuolojen-kaytto (nth (butlast vastaus) 2)]
    (is (vector? vastaus))
    (let [otsikko "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (is (= talvisuolojen-kaytto [:teksti "Erilaisia talvisuoloja käytetty valitulla aikavälillä: 0,00t"]) "talvisuolan toteutunut määrä")
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
                                          {:otsikko "Suunniteltu määrä / talvisuolan max-määrä"})
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
                                                      :urakkatyyppi :hoito}})]
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
                                          {:otsikko "Suunniteltu määrä / talvisuolan max-määrä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :ymparistoraportti
                                 :konteksti  "koko maa"
                                 :parametrit {:alkupvm      (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm     (c/to-date (t/local-date 2016 9 30))
                                              :urakkatyyppi :hoito}})]
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
                                          {:otsikko "Suunniteltu määrä / talvisuolan max-määrä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest ymparisto-materiaali-ja-suolaraportin-tulokset-tasmaavat
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        param {:alkupvm (c/to-date (t/local-date 2014 10 1))
               :loppupvm (c/to-date (t/local-date 2015 9 30))
               :urakkatyyppi :hoito}
        ymparisto (apurit/taulukko-otsikolla
                    (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :suorita-raportti
                                    +kayttaja-jvh+
                                    {:nimi :ymparistoraportti
                                     :konteksti "urakka"
                                     :urakka-id urakka-id
                                     :parametrit param})
                    "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.10.2014 - 30.09.2015")
        ymp-kaytetty-suola (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 5 1))
        ymp-kaytetty-suolaliuos (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 5 3))
        ymp-kaytetty-natriumformiaatti (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 5 11))
        ymp-suolaliuos-yht (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 13 3))
        ymp-kaikki-talvisuola-helmikuu (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 5 8))
        ymp-kaikki-talvisuola-yht (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 13 8))
        ymp-hiekka-totpros (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 14 14))
        ymp-hiekka-suunniteltu (apurit/raporttisolun-arvo (apurit/taulukon-solu ymparisto 15 14))
        materiaali (apurit/taulukko-otsikolla
                     (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :suorita-raportti
                                     +kayttaja-jvh+
                                     {:nimi :materiaaliraportti
                                      :konteksti "urakka"
                                      :urakka-id urakka-id
                                      :parametrit param})
                     "Oulun alueurakka 2014-2019, Materiaaliraportti ajalta 01.10.2014 - 30.09.2015")
        mat-kaytetty-kaikki-talvisuola (apurit/taulukon-solu materiaali 1 0)
        mat-kaytetty-suola (apurit/taulukon-solu materiaali 2 0)
        mat-kaytetty-talvisuolaliuos (apurit/taulukon-solu materiaali 3 0)
        mat-kaytetty-natriumformiaatti (apurit/taulukon-solu materiaali 4 0)
        mat-kaytetty-hiekka (apurit/taulukon-solu materiaali 5 0)
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
    (is (= ymp-kaytetty-suolaliuos ymp-suolaliuos-yht mat-kaytetty-talvisuolaliuos 1800M)
        "Ympäristö- ja materiaaliraportin pitäisi laskea käytetty Talvisuolaliuos NaCl samalla tavalla")
    (is (= ymp-kaytetty-natriumformiaatti mat-kaytetty-natriumformiaatti 2000M)
        "Ympäristö- ja materiaaliraportin pitäisi laskea käytetty Natriumformiaatti samalla tavalla")
    (is (= suola-kaytetty-suola
           (+ ymp-kaytetty-suolaliuos ymp-kaytetty-suola)
           (+ mat-kaytetty-suola mat-kaytetty-talvisuolaliuos)
           2000M) "Ympäristö- ja materiaaliraportin pitäisi laskea käytetty Natriumformiaatti samalla tavalla")
    (is (= 2000M ymp-kaikki-talvisuola-helmikuu ymp-kaikki-talvisuola-yht) "Kaikki talvisuola yhteensä")
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



(deftest ymparistoraportin-hoitoluokittaiset-maarat
  (let [vastaus-pop-ely (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :suorita-raportti
                                        +kayttaja-jvh+
                                        {:nimi               :ymparistoraportti
                                         :konteksti          "hallintayksikko"
                                         :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                         :parametrit         {:alkupvm      (c/to-date (t/local-date 2017 10 1))
                                                              :loppupvm     (c/to-date (t/local-date 2018 9 30))
                                                              :urakkatyyppi :hoito}})
        vastaus-oulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :suorita-raportti
                                        +kayttaja-jvh+
                                        {:nimi               :ymparistoraportti
                                         :konteksti          "urakka"
                                         :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                         :parametrit         {:alkupvm      (c/to-date (t/local-date 2017 10 1))
                                                              :loppupvm     (c/to-date (t/local-date 2018 9 30))
                                                              :urakkatyyppi :hoito}})]

    (is (vector? vastaus-pop-ely))
    (let [otsikko-pop-ely "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.10.2017 - 30.09.2018"
          taulukko-pop-ely (apurit/taulukko-otsikolla vastaus-pop-ely otsikko-pop-ely)
          pop-ely-talvisuola-luokka-IsE (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 5 2))
          pop-ely-talvisuola-luokka-Is (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 5 3))
          pop-ely-talvisuola-luokka-I (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 5 4))
          pop-ely-talvisuola-luokka-Ib (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 5 5))
          pop-ely-talvisuola-luokka-TIb (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-pop-ely 5 6))

          otsikko-oulu "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.10.2017 - 30.09.2018"
          taulukko-oulu (apurit/taulukko-otsikolla vastaus-oulu otsikko-oulu)
          oulu-talvisuola-luokka-IsE (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 5 2))
          oulu-talvisuola-luokka-Is (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 5 3))
          oulu-talvisuola-luokka-I (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 5 4))
          oulu-talvisuola-luokka-Ib (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 5 5))
          oulu-talvisuola-luokka-TIb (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 5 6))]

      (is (= pop-ely-talvisuola-luokka-IsE 600M))
      (is (= pop-ely-talvisuola-luokka-Is 400M))
      (is (= pop-ely-talvisuola-luokka-I 400M))
      (is (= pop-ely-talvisuola-luokka-Ib 400M))
      (is (= pop-ely-talvisuola-luokka-TIb 200M))

      (is (= oulu-talvisuola-luokka-IsE 300M))
      (is (= oulu-talvisuola-luokka-Is 200M))
      (is (= oulu-talvisuola-luokka-I 200M))
      (is (= oulu-talvisuola-luokka-Ib 200M))
      (is (= oulu-talvisuola-luokka-TIb 100M))

      (apurit/tarkista-taulukko-sarakkeet taulukko-pop-ely
                                          {:otsikko "Materiaali"}
                                          {:otsikko "10/17"}
                                          {:otsikko "11/17"}
                                          {:otsikko "12/17"}
                                          {:otsikko "01/18"}
                                          {:otsikko "02/18"}
                                          {:otsikko "03/18"}
                                          {:otsikko "04/18"}
                                          {:otsikko "05/18"}
                                          {:otsikko "06/18"}
                                          {:otsikko "07/18"}
                                          {:otsikko "08/18"}
                                          {:otsikko "09/18"}
                                          {:otsikko "Määrä yhteensä"}
                                          {:otsikko "Tot-%"}
                                          {:otsikko "Suunniteltu määrä / talvisuolan max-määrä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko-pop-ely tarkistusfunktio))))


(deftest ymparistoraportin-hoitoluokittaiset-maarat-vanha-ja-uusi-koodisto-sekaisin-oulu
  (let [vastaus-oulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :suorita-raportti
                                     +kayttaja-jvh+
                                     {:nimi               :ymparistoraportti
                                      :konteksti          "urakka"
                                      :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                      :parametrit         {:alkupvm     (pvm/->pvm "1.1.2018")
                                                           :loppupvm     (pvm/->pvm "31.12.2018")
                                                           :urakkatyyppi :hoito}})]

    (is (vector? vastaus-oulu))
    (let [otsikko-oulu "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.01.2018 - 31.12.2018"
          taulukko-oulu (apurit/taulukko-otsikolla vastaus-oulu otsikko-oulu)
          oulu-talvisuola-luokka-kaikki-hoitoluokat-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 1))
          oulu-talvisuola-luokka-IsE-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 2))
          oulu-talvisuola-luokka-Is-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 3))
          oulu-talvisuola-luokka-I-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 4))
          oulu-talvisuola-luokka-Ib-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 5))
          oulu-talvisuola-luokka-TIb-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 6))
          oulu-talvisuola-luokka-II-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 7))
          oulu-talvisuola-luokka-III-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 8))
          oulu-talvisuola-luokka-L-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 9))
          oulu-talvisuola-luokka-K1-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 10))
          oulu-talvisuola-luokka-K2-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 11))
          oulu-talvisuola-luokka-ei-talvih-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 2 12))

          oulu-talvisuola-luokka-kaikki-hoitoluokat-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 1))
          oulu-talvisuola-luokka-IsE-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 2))
          oulu-talvisuola-luokka-Is-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 3))
          oulu-talvisuola-luokka-I-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 4))
          oulu-talvisuola-luokka-Ib-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 5))
          oulu-talvisuola-luokka-TIb-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 6))
          oulu-talvisuola-luokka-II-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 7))
          oulu-talvisuola-luokka-III-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 8))
          oulu-talvisuola-luokka-L-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 9))
          oulu-talvisuola-luokka-K1-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 10))
          oulu-talvisuola-luokka-K2-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 11))
          oulu-talvisuola-luokka-ei-talvih-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko-oulu 10 12))]

      (is (= oulu-talvisuola-luokka-kaikki-hoitoluokat-02-18 1500M))
      (is (= oulu-talvisuola-luokka-IsE-02-18 300M))
      (is (= oulu-talvisuola-luokka-Is-02-18 200M))
      (is (= oulu-talvisuola-luokka-I-02-18 200M))
      (is (= oulu-talvisuola-luokka-Ib-02-18 200M))
      (is (= oulu-talvisuola-luokka-TIb-02-18 100M))
      (is (= oulu-talvisuola-luokka-II-02-18 100M))
      (is (= oulu-talvisuola-luokka-III-02-18 100M))
      (is (nil? oulu-talvisuola-luokka-L-02-18))
      (is (= oulu-talvisuola-luokka-K1-02-18 100M))
      (is (= oulu-talvisuola-luokka-K2-02-18 100M))
      (is (= oulu-talvisuola-luokka-ei-talvih-02-18 100M))

      (is (= oulu-talvisuola-luokka-kaikki-hoitoluokat-10-18 1100M))
      (is (= oulu-talvisuola-luokka-IsE-10-18 100M))
      (is (= oulu-talvisuola-luokka-Is-10-18 100M))
      (is (= oulu-talvisuola-luokka-I-10-18 100M))
      (is (= oulu-talvisuola-luokka-Ib-10-18 100M))
      (is (= oulu-talvisuola-luokka-TIb-10-18 100M))
      (is (= oulu-talvisuola-luokka-II-10-18 100M))
      (is (= oulu-talvisuola-luokka-III-10-18 100M))
      (is (= oulu-talvisuola-luokka-L-10-18 100M))
      (is (= oulu-talvisuola-luokka-K1-10-18 100M))
      (is (= oulu-talvisuola-luokka-K2-10-18 100M))
      (is (= oulu-talvisuola-luokka-ei-talvih-10-18 100M))

      (apurit/tarkista-taulukko-sarakkeet taulukko-oulu
                                          {:otsikko "Materiaali"}
                                          {:otsikko "01/18"}
                                          {:otsikko "02/18"}
                                          {:otsikko "03/18"}
                                          {:otsikko "04/18"}
                                          {:otsikko "05/18"}
                                          {:otsikko "06/18"}
                                          {:otsikko "07/18"}
                                          {:otsikko "08/18"}
                                          {:otsikko "09/18"}
                                          {:otsikko "10/18"}
                                          {:otsikko "11/18"}
                                          {:otsikko "12/18"}
                                          {:otsikko "Määrä yhteensä"}
                                          {:otsikko "Tot-%"}
                                          {:otsikko "Suunniteltu määrä / talvisuolan max-määrä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko-oulu tarkistusfunktio))))


(deftest ymparistoraportin-hoitoluokittaiset-maarat-vanha-ja-uusi-koodisto-sekaisin-pop-ely-ei-urakoittain
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :suorita-raportti
                                     +kayttaja-jvh+
                                     {:nimi               :ymparistoraportti
                                      :konteksti          "hallintayksikko"
                                      :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                      :parametrit         {:alkupvm     (pvm/->pvm "1.1.2018")
                                                           :loppupvm     (pvm/->pvm "31.12.2018")
                                                           :urakkatyyppi :hoito
                                                           :urakoittain? false}})]

    (is (vector? vastaus))
    (let [otsikko "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.01.2018 - 31.12.2018"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)
          talvisuola-luokka-kaikki-hoitoluokat-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 1))
          talvisuola-luokka-IsE-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 2))
          talvisuola-luokka-Is-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 3))
          talvisuola-luokka-I-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 4))
          talvisuola-luokka-Ib-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 5))
          talvisuola-luokka-TIb-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 6))
          talvisuola-luokka-II-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 7))
          talvisuola-luokka-III-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 8))
          talvisuola-luokka-L-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 9))
          talvisuola-luokka-K1-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 10))
          talvisuola-luokka-K2-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 11))
          talvisuola-luokka-ei-talvih-02-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 2 12))

          talvisuola-luokka-kaikki-hoitoluokat-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 1))
          talvisuola-luokka-IsE-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 2))
          talvisuola-luokka-Is-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 3))
          talvisuola-luokka-I-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 4))
          talvisuola-luokka-Ib-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 5))
          talvisuola-luokka-TIb-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 6))
          talvisuola-luokka-II-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 7))
          talvisuola-luokka-III-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 8))
          talvisuola-luokka-L-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 9))
          talvisuola-luokka-K1-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 10))
          talvisuola-luokka-K2-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 11))
          talvisuola-luokka-ei-talvih-10-18 (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 10 12))]

      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18 2500M))
      (is (= talvisuola-luokka-IsE-02-18 600M))
      (is (= talvisuola-luokka-Is-02-18 400M))
      (is (= talvisuola-luokka-I-02-18 400M))
      (is (= talvisuola-luokka-Ib-02-18 400M))
      (is (= talvisuola-luokka-TIb-02-18 200M))
      (is (= talvisuola-luokka-II-02-18 100M))
      (is (= talvisuola-luokka-III-02-18 100M))
      (is (nil? talvisuola-luokka-L-02-18))
      (is (= talvisuola-luokka-K1-02-18 100M))
      (is (= talvisuola-luokka-K2-02-18 100M))
      (is (= talvisuola-luokka-ei-talvih-02-18 100M))

      (is (= talvisuola-luokka-kaikki-hoitoluokat-10-18 1100M))
      (is (= talvisuola-luokka-IsE-10-18 100M))
      (is (= talvisuola-luokka-Is-10-18 100M))
      (is (= talvisuola-luokka-I-10-18 100M))
      (is (= talvisuola-luokka-Ib-10-18 100M))
      (is (= talvisuola-luokka-TIb-10-18 100M))
      (is (= talvisuola-luokka-II-10-18 100M))
      (is (= talvisuola-luokka-III-10-18 100M))
      (is (= talvisuola-luokka-L-10-18 100M))
      (is (= talvisuola-luokka-K1-10-18 100M))
      (is (= talvisuola-luokka-K2-10-18 100M))
      (is (= talvisuola-luokka-ei-talvih-10-18 100M))

      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Materiaali"}
                                          {:otsikko "01/18"}
                                          {:otsikko "02/18"}
                                          {:otsikko "03/18"}
                                          {:otsikko "04/18"}
                                          {:otsikko "05/18"}
                                          {:otsikko "06/18"}
                                          {:otsikko "07/18"}
                                          {:otsikko "08/18"}
                                          {:otsikko "09/18"}
                                          {:otsikko "10/18"}
                                          {:otsikko "11/18"}
                                          {:otsikko "12/18"}
                                          {:otsikko "Määrä yhteensä"}
                                          {:otsikko "Tot-%"}
                                          {:otsikko "Suunniteltu määrä / talvisuolan max-määrä"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko tarkistusfunktio))))

(deftest ymparistoraportin-hoitoluokittaiset-maarat-vanha-ja-uusi-koodisto-sekaisin-pop-ely-urakoittain
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi               :ymparistoraportti
                                 :konteksti          "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit         {:alkupvm     (pvm/->pvm "1.1.2018")
                                                      :loppupvm     (pvm/->pvm "31.12.2018")
                                                      :urakkatyyppi :hoito
                                                      :urakoittain? true}})]

    (is (vector? vastaus))
    (let [otsikko "Pohjois-Pohjanmaa, Ympäristöraportti ajalta 01.01.2018 - 31.12.2018"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)
          talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 1))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-IsE (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 2))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-Is (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 3))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-I (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 4))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-Ib (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 5))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-TIb (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 6))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 7))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-IsE (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 2))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-Is (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 3))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-I (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 4))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-Ib (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 5))
          talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-TIb (apurit/raporttisolun-arvo (apurit/taulukon-solu taulukko 3 6))]

      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani 1000M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-IsE 300M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-Is 200M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-I 200M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-Ib 200M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-kajaani-TIb 100M))

      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu 1500M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-IsE 300M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-Is 200M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-I 200M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-Ib 200M))
      (is (= talvisuola-luokka-kaikki-hoitoluokat-02-18-oulu-TIb 100M))


      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Urakka"}
                                          {:otsikko "Materiaali"}
                                          {:otsikko "01/18"}
                                          {:otsikko "02/18"}
                                          {:otsikko "03/18"}
                                          {:otsikko "04/18"}
                                          {:otsikko "05/18"}
                                          {:otsikko "06/18"}
                                          {:otsikko "07/18"}
                                          {:otsikko "08/18"}
                                          {:otsikko "09/18"}
                                          {:otsikko "10/18"}
                                          {:otsikko "11/18"}
                                          {:otsikko "12/18"}
                                          {:otsikko "Määrä yhteensä"}
                                          {:otsikko "Tot-%"}
                                          {:otsikko "Suunniteltu määrä / talvisuolan max-määrä"}))))

;; Testaa että talvihoitoluokan normalisointisproc-toimii odotetusti.
;;Muutospvm aineistossa 2.7.2018 jonka mukaan vipu vääntyy.
(deftest normalisoi-talvihoitoluokka
  (let [vanha-IsE (ffirst (q "select * from normalisoi_talvihoitoluokka(0, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-IsE (ffirst (q "select * from normalisoi_talvihoitoluokka(1, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-Is (ffirst (q "select * from normalisoi_talvihoitoluokka(1, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-Is (ffirst (q "select * from normalisoi_talvihoitoluokka(2, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-I (ffirst (q "select * from normalisoi_talvihoitoluokka(2, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-I (ffirst (q "select * from normalisoi_talvihoitoluokka(3, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-Ib (ffirst (q "select * from normalisoi_talvihoitoluokka(3, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-Ib (ffirst (q "select * from normalisoi_talvihoitoluokka(4, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-TIb (ffirst (q "select * from normalisoi_talvihoitoluokka(4, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-Ic (ffirst (q "select * from normalisoi_talvihoitoluokka(5, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-II (ffirst (q "select * from normalisoi_talvihoitoluokka(5, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-II (ffirst (q "select * from normalisoi_talvihoitoluokka(6, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-III (ffirst (q "select * from normalisoi_talvihoitoluokka(6, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-III (ffirst (q "select * from normalisoi_talvihoitoluokka(7, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-K1 (ffirst (q "select * from normalisoi_talvihoitoluokka(7, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-K1 (ffirst (q "select * from normalisoi_talvihoitoluokka(9, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        vanha-K2 (ffirst (q "select * from normalisoi_talvihoitoluokka(8, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-K2 (ffirst (q "select * from normalisoi_talvihoitoluokka(10, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))

        vanha-ei-talvihoitoa (ffirst (q "select * from normalisoi_talvihoitoluokka(9, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-ei-talvihoitoa (ffirst (q "select * from normalisoi_talvihoitoluokka(11, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))

        vanha-III (ffirst (q "select * from normalisoi_talvihoitoluokka(6, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        uusi-III (ffirst (q "select * from normalisoi_talvihoitoluokka(7, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))

        ei-talvihoitoluokkaa-vanha (ffirst (q "select * from normalisoi_talvihoitoluokka(null, '2017-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        ei-talvihoitoluokkaa-uusi (ffirst (q "select * from normalisoi_talvihoitoluokka(null, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        ei-talvihoitoluokkaa-outonumero (ffirst (q "select * from normalisoi_talvihoitoluokka(667, '2018-07-3T09:42:04.123-00:00'::TIMESTAMP);"))
        ]
    (is (= vanha-IsE uusi-IsE 1) "IsE")
    (is (= vanha-Is uusi-Is 2) "Is")
    (is (= vanha-I uusi-I 3) "I")
    (is (= vanha-Ib uusi-Ib 4) "Ib")
    (is (= vanha-TIb uusi-Ic 5) "TIb")
    (is (= vanha-II uusi-II 6) "II")
    (is (= vanha-III uusi-III 7) "III")
    (is (= vanha-K1 uusi-K1 9) "K1")
    (is (= vanha-K2 uusi-K2 10) "K2")
    (is (= vanha-ei-talvihoitoa uusi-ei-talvihoitoa 11) "ei talvihoitoa")
    (is (= ei-talvihoitoluokkaa-vanha ei-talvihoitoluokkaa-uusi ei-talvihoitoluokkaa-outonumero 100) "ei talvihoitoluokkaa")))