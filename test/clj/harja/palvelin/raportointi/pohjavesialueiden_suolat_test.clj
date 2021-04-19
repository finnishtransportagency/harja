(ns harja.palvelin.raportointi.pohjavesialueiden-suolat-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit
             [tietokanta :as tietokanta]
             [pdf-vienti :as pdf-vienti]]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.raportointi.testiapurit :as apurit]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.kyselyt.raportit :as raportit-q]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

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
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn tarkista-sarakkeet [taulukko]
  (apurit/tarkista-taulukko-sarakkeet
    taulukko
    {:leveys 3 :fmt :kokonaisluku :otsikko "Tie"}
    {:leveys 2 :fmt :kokonaisluku :otsikko "Alku\u00ADosa"}
    {:leveys 2 :fmt :kokonaisluku :otsikko "Alku\u00ADetäisyys"}
    {:leveys 2 :fmt :kokonaisluku :otsikko "Loppu\u00ADosa"}
    {:leveys 2 :fmt :kokonaisluku :otsikko "Loppu\u00ADetäisyys"}
    {:leveys 3 :fmt :numero :otsikko "Pituus"}
    {:leveys 5 :fmt :numero :otsikko "Tot. talvisuola yhteensä (t)"}
    {:leveys 5 :fmt :numero :otsikko "Tot. talvisuola (t/km)"}
    {:leveys 5 :fmt :numero :otsikko "Käyttö\u00ADraja (t/km)"}))

(deftest raportin-suoritus-urakalle-toimii
  (let [nyt (java.util.Date.)
        v (+ (.getYear nyt) 1900)
        kk (inc (.getMonth nyt))
        p (.getDate nyt)
        str-p (if (= 1 (count (str p)))
            (str "0" p)
            p)
        str-kk (if (= 1 (count (str kk)))
            (str "0" kk)
            kk)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :pohjavesialueiden-suolatoteumat
                                 :konteksti "urakka"
                                 :urakka-id (ffirst (q (str "SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi';")))
                                 :parametrit {:alkupvm (c/to-date (t/local-date (dec v) kk p))
                                              :loppupvm (c/to-date (t/local-date (inc v) kk p))}})]
    (is (vector? vastaus))
    (let [taulukko (apurit/taulukko-otsikolla
                     vastaus
                     "11244001-Kempeleenharju")]

      (tarkista-sarakkeet taulukko)

      (is (= vastaus
             [:raportti
              {:nimi (str "Aktiivinen Oulu Testi, Pohjavesialueiden suolatoteumat ajalta "str-p"."str-kk".2020 - "str-p"."str-kk".2022")
               :orientaatio :landscape}
              [:taulukko
               {:otsikko "11244001-Kempeleenharju"
                :tyhja nil
                :viimeinen-rivi-yhteenveto? true}
               [{:fmt :kokonaisluku
                 :leveys 3
                 :otsikko "Tie"}
                {:fmt :kokonaisluku
                 :leveys 2
                 :otsikko "Alku­osa"}
                {:fmt :kokonaisluku
                 :leveys 2
                 :otsikko "Alku­etäisyys"}
                {:fmt :kokonaisluku
                 :leveys 2
                 :otsikko "Loppu­osa"}
                {:fmt :kokonaisluku
                 :leveys 2
                 :otsikko "Loppu­etäisyys"}
                {:fmt :numero
                 :leveys 3
                 :otsikko "Pituus"}
                {:fmt :numero
                 :leveys 5
                 :otsikko "Tot. talvisuola yhteensä (t)"}
                {:fmt :numero
                 :leveys 5
                 :otsikko "Tot. talvisuola (t/km)"}
                {:fmt :numero
                 :leveys 5
                 :otsikko "Käyttö­raja (t/km)"}]
               [["846"
                 "1"
                 "0"
                 "1"
                 "1016"
                 "1389,3"
                 "3,0"
                 "2,2"
                 ""]
                ["18637"
                 "1"
                 "0"
                 "1"
                 "8953"
                 "9324,4"
                 "5,0"
                 "0,5"
                 ""]
                ["Yhteensä"
                 ""
                 ""
                 ""
                 ""
                 ""
                 "8,0"
                 ""
                 ""]]]])))))