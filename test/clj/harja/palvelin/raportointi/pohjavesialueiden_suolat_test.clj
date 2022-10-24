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
    {}))

(deftest raportin-suoritus-urakalle-toimii-aktiivinen-urakka
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
            [:raportti {:orientaatio :landscape
                        :nimi (str "Aktiivinen Oulu Testi, Suolatoteumat (kaikki pohjavesialueet) ajalta " str-p "." str-kk "." (dec v) " - " str-p "." str-kk "." (inc v))}
             [:infolaatikko "Huom: tämä raportti on vanhentunut rajoitusaluetietojen osalta. Tarkista voimassaolevat rajoitusalueet raportilta 'Suolatoteumat - urakan rajoitusalueet'." {:tyyppi :vahva-ilmoitus}]
             [:taulukko {:otsikko "11244001-Kempeleenharju", :viimeinen-rivi-yhteenveto? true, :tyhja nil}
              [{:leveys 3 :fmt :kokonaisluku :otsikko "Tie"}
               {:leveys 2 :fmt :kokonaisluku :otsikko "Alku­osa"}
               {:leveys 2 :fmt :kokonaisluku :otsikko "Alku­etäisyys"}
               {:leveys 2 :fmt :kokonaisluku :otsikko "Loppu­osa"}
               {:leveys 2 :fmt :kokonaisluku :otsikko "Loppu­etäisyys"}
               {:leveys 3 :fmt :numero :otsikko "Pituus"}
               {:leveys 5 :fmt :numero :otsikko "Tot. talvisuola yhteensä (t)"}
               {:leveys 5 :fmt :numero :otsikko "Tot. talvisuola (t/km)"}
               nil]
              [[18637 1 0 1 8953 9324.379134279012 5M 0.5362287320148329 nil]
               [28409 56 0 56 605 573.3889406769638 3M 5.2320506852784625 nil]
               ["Yhteensä" nil nil nil nil nil 8M nil nil]]]])))))
