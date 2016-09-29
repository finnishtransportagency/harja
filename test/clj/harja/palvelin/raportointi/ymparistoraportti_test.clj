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
        hoitokaudet (drop-last 3 (rest rivi))]
    (and (= (count rivi) 16)
         (string? materiaali)
         (every? #(or (nil? %) (number? %)) hoitokaudet)
         (or (nil? yhteensa) (number? yhteensa))
         (or (nil? suunniteltu) (number? suunniteltu))
         (or (nil? prosentti) (string? prosentti))
         (or (and (every? nil? hoitokaudet) (nil? yhteensa))
             (= (reduce (fnil + 0 0) hoitokaudet) yhteensa))
         (or
           (nil? prosentti) (nil? yhteensa) (nil? suunniteltu)
           (= (/ (* 100.0 yhteensa) suunniteltu)
              (Integer/parseInt (re-find #"\d+" prosentti)))))))


(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :ymparistoraportti
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm  (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 9 30))}})]
    (is (vector? vastaus))
    (let [otsikko "Oulun alueurakka 2014-2019, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
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
    (let [otsikko "Pohjois-Pohjanmaa ja Kainuu, Ympäristöraportti ajalta 01.10.2015 - 30.09.2016"
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