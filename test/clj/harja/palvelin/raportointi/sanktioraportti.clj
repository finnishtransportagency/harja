(ns harja.palvelin.raportointi.sanktioraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]))

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

(deftest raportin-suoritus-hallintayksikolle-toimii
  "Todella tiukka testi, joka testaa, että testidatasta muodostuu raportti oikein.

  Jos testi alkaa failata, tee diff testin odottaman vastauksen ja raportin palauttaman datan välillä.
  Korjaa muutokset tai kopioi raporttipalvelun palauttama data tänne."
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :sanktioraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2011 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Sanktioraportti", :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Pohjois-Pohjanmaa, Sanktioraportti ajalta 01.10.2011 - 01.10.2016"}
                     [{:otsikko "", :leveys 10}
                      {:otsikko "Yks.", :leveys 4}
                      {:otsikko "Oulun alueurakka 2014-2019", :leveys 20}
                      {:otsikko "Pudasjärven alueurakka 2007-2012", :leveys 20}
                      {:otsikko "Yhteensä", :leveys 10}]
                     [{:otsikko "Talvihoito"}
                      ["Muistutukset" "kpl" 1 1 2]
                      ["Sakko A" "€" "1000,00" "10000,00" "11000,00"]
                      ["- Päätiet" "€" "1000,00" "10000,00" "11000,00"]
                      ["- Muut tiet" "€" "0,00" "0,00" "0,00"]
                      ["Sakko B" "€" "666,67" "6660,00" "7326,67"]
                      ["- Päätiet" "€" "666,67" "6660,00" "7326,67"]
                      ["- Muut tiet" "€" "0,00" "0,00" "0,00"]
                      ["Talvihoito, sakot yht." "€" "1666,67" "16660,00" "18326,67"]
                      ["Talvihoito, indeksit yht." "€" "1674,11" "0,00" "1674,11"]
                      {:otsikko "Muut tuotteet"}
                      ["Muistutukset" "kpl" 1 1 2]
                      ["Sakko A" "€" "0,00" "0,00" "0,00"]
                      ["- Liikenneymp. hoito" "€" "0,00" "0,00" "0,00"]
                      ["- Sorateiden hoito" "€" "0,00" "0,00" "0,00"]
                      ["Sakko B" "€" "111,00" "1110,00" "1221,00"]
                      ["- Liikenneymp. hoito" "€" "110,00" "1100,00" "1210,00"]
                      ["- Sorateiden hoito" "€" "0,00" "0,00" "0,00"]
                      ["Muut tuotteet, sakot yht." "€" "111,00" "1110,00" "1221,00"]
                      ["Muut tuotteet, indeksit yht." "€" "111,50" "0,00" "111,50"]
                      {:otsikko "Ryhmä C"}
                      ["Ryhmä C, sakot yht." "€" "123,00" "1230,00" "1353,00"]
                      ["Ryhmä C, indeksit yht." "€" "123,55" "0,00" "123,55"]
                      {:otsikko "Yhteensä"}
                      ["Muistutukset yht." "kpl" 2 2 4]
                      ["Indeksit yht." "€" "1909,16" "0,00" "1909,16"]
                      ["Kaikki sakot yht." "€" "1900,67" "19000,00" "20900,67"]
                      ["Kaikki yht." "€" "3809,83" "19000,00" "22809,83"]]]]))))