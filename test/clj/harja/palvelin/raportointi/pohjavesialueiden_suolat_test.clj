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
  (raportit-q/paivita_raportti_pohjavesialueiden_suolatoteumat (:db jarjestelma))
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
    {:leveys 5 :fmt :numero :otsikko "Käyttö­raja (t/km)"}))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :pohjavesialueiden-suolatoteumat
                                 :konteksti "urakka"
                                 :urakka-id (ffirst (q (str "SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012';")))
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2007 10 1))
                                              :loppupvm (c/to-date (t/local-date 2008 9 30))}})]
    (is (vector? vastaus))
    (let [taulukko (apurit/taulukko-otsikolla
                     vastaus
                     "11615174-")]

      (tarkista-sarakkeet taulukko)

      (is (= vastaus
            [:raportti {:orientaatio :landscape
                        :nimi "Pudasjärven alueurakka 2007-2012, Suolatoteumat (kaikki pohjavesialueet) ajalta 01.10.2007 - 30.09.2008"}
             nil
             [:taulukko {:otsikko "11615174-", :viimeinen-rivi-yhteenveto? true, :tyhja nil}
              [{:leveys 3 :fmt :kokonaisluku :otsikko "Tie"}
               {:leveys 2 :fmt :kokonaisluku :otsikko "Alku­osa"}
               {:leveys 2 :fmt :kokonaisluku :otsikko "Alku­etäisyys"}
               {:leveys 2 :fmt :kokonaisluku :otsikko "Loppu­osa"}
               {:leveys 2 :fmt :kokonaisluku :otsikko "Loppu­etäisyys"}
               {:leveys 3 :fmt :numero :otsikko "Pituus"}
               {:leveys 5 :fmt :numero :otsikko "Tot. talvisuola yhteensä (t)"}
               {:leveys 5 :fmt :numero :otsikko "Tot. talvisuola (t/km)"}
               {:leveys 5 :fmt :numero :otsikko "Käyttö­raja (t/km)"}]
              [[20 14 8098 14 11028 2931.4154579352903 12.64234230994867M 4.3127091643479165 nil]
               [[:arvo {:arvo "Yhteensä"}] nil nil nil nil nil 12.64234230994867M nil nil]]]])))))
