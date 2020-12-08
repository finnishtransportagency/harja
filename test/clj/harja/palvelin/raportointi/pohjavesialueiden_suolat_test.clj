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
  (pystyta-harja-tarkkailija!)
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
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn tarkista-sarakkeet [taulukko]
  (apurit/tarkista-taulukko-sarakkeet
    taulukko
    {:otsikko "Tie"}
    {:otsikko "Alkuosa"}
    {:otsikko "Alkuetäisyys"}
    {:otsikko "Loppuosa"}
    {:otsikko "Loppuetäisyys"}
    {:otsikko "Pituus"}
    {:otsikko "Toteutunut talvisuola yhteensä t"}
    {:otsikko "Toteutunut talvisuola t/km"}
    {:otsikko "Käyttöraja t/km"}))

(deftest raportin-suoritus-urakalle-toimii
  (let [nyt (java.util.Date.)
        v (+ (.getYear nyt) 1900)
        kk (inc (.getMonth nyt))
        p (.getDate nyt)
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
              {:orientaatio :landscape,
               :nimi
               (str "Aktiivinen Oulu Testi, Pohjavesialueiden suolatoteumat ajalta "
                    (apply str (interpose "."
                                          [(format "%02d" p) (format "%02d" kk) (dec v)]))
                    " - "
                    (apply str (interpose "."
                                          [(format "%02d" p) (format "%02d" kk) (inc v)])))}
              [:taulukko
               {:otsikko "11244001-Kempeleenharju",
                :viimeinen-rivi-yhteenveto? true}
               [{:leveys 3, :otsikko "Tie"}
                {:leveys 2, :otsikko "Alkuosa"}
                {:leveys 2, :otsikko "Alkuetäisyys"}
                {:leveys 2, :otsikko "Loppuosa"}
                {:leveys 2, :otsikko "Loppuetäisyys"}
                {:leveys 3, :otsikko "Pituus"}
                {:leveys 5, :otsikko "Toteutunut talvisuola yhteensä t"}
                {:leveys 5, :otsikko "Toteutunut talvisuola t/km"}
                {:leveys 5, :otsikko "Käyttöraja t/km"}]
               [["846" "1" "0" "1" "1016" "1389,3" "3,0" "2,2" ""]
                ["18637" "1" "0" "1" "8953" "9324,4" "5,0" "0,5" ""]
                ["Yhteensä" "" "" "" "" "" "8,0" "" ""]]]])))))