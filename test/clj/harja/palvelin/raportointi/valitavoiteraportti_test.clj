(ns harja.palvelin.raportointi.valitavoiteraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.core.match :refer [match]]
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


(deftest raportin-suoritus-urakalle-toimii
  (let [parametrit {:alkupvm #inst "2014-10-01T22:00:00.000-00:00"
                    :loppupvm #inst "2015-09-30T21:59:59.000-00:00"}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :valitavoiteraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit parametrit})
        raportin-otsikko (-> vastaus second :nimi)
        urakkakohtainen-taulukko (nth vastaus 4)
        urakkakohtaiset-otsikot (nth urakkakohtainen-taulukko 2)
        urakkakohtaiset-valitavoitteet (nth urakkakohtainen-taulukko 3)
        kaikkien-urakoiden-valitavoitteiden-taulukko (nth vastaus 5)
        kaikkien-urakoiden-valitavoitteiden-otsikot (nth kaikkien-urakoiden-valitavoitteiden-taulukko 2)
        kaikkien-urakoiden-valitavoitteet (nth kaikkien-urakoiden-valitavoitteiden-taulukko 3)]
    (is (vector? vastaus))
    (is (= raportin-otsikko "Välitavoiteraportti"))
    (is (= urakkakohtaiset-otsikot
          [{:otsikko "Nimi", :leveys 10} nil
           {:otsikko "Takaraja", :leveys 5}
           {:otsikko "Tila", :leveys 5}
           {:otsikko "Valmistumispäivä", :leveys 5}
           {:otsikko "Kommentti valmistumisesta", :leveys 10}
           {:otsikko "Valmiiksimerkitsijä", :leveys 5}]))
    (is (= (count urakkakohtaiset-valitavoitteet) 2))
    (is (= kaikkien-urakoiden-valitavoitteiden-otsikot)
          [{:otsikko "Työn kuvaus", :leveys 8}
           {:otsikko "Urakkakohtaiset tarkennukset", :leveys 8}
           {:otsikko "Valtakunnallinen takaraja", :leveys 5}
           {:otsikko "Takaraja urakassa", :leveys 5}
           {:otsikko "Tila", :leveys 5}
           {:otsikko "Valmistumispäivä", :leveys 5}
           {:otsikko "Kommentti valmistumisesta", :leveys 8} {:otsikko "Merkitsijä", :leveys 5}])
    (is (= (count urakkakohtaiset-valitavoitteet) 2))
    (is (= (count kaikkien-urakoiden-valitavoitteet) 0))))
