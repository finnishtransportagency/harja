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
                                 :parametrit parametrit})]
    (is (vector? vastaus))
    (is (match vastaus
          [:raportti {:orientaatio :landscape, :nimi "Välitavoiteraportti", :rajoita-pdf-rivimaara nil}
           [:otsikko-heading-small "Oulun alueurakka 2014-2019, Välitavoiteraportti ajalta 01.10.2014 - 30.09.2016, suoritettu 06.08.2026" nil]
           [:taulukko
            {:otsikko "Urakkakohtaiset määräaikaan mennessä tehtävät työt", :tyhja nil, :sheet-nimi "Välitavoiteraportti"}
            [{:otsikko "Nimi", :leveys 10} nil
             {:otsikko "Takaraja", :leveys 5}
             {:otsikko "Tila", :leveys 5}
             {:otsikko "Valmistumispäivä", :leveys 5}
             {:otsikko "Kommentti valmistumisesta", :leveys 10}
             {:otsikko "Valmiiksimerkitsijä", :leveys 5}]
            [["Sepon mökkitie suolattu" nil "24.12.2014 (12 vuotta myöhässä)" "Myöhässä (12v)" "-" nil " "]
             ["Pelkosentie 678 suolattu" nil "23.09.2015 (11 vuotta myöhässä)" "Valmistunut" "25.09.2015" "Aurattu, mutta vähän tuli myöhässä" " "]]]
           [:taulukko {:otsikko "Kaikissa urakoissa määräaikaan mennessä tehtävät työt", :tyhja "Ei raportoitavia määräaikaan mennessä tehtäviä töitä.", :sheet-nimi "Välitavoiteraportti"}
            [{:otsikko "Työn kuvaus", :leveys 8}
             {:otsikko "Urakkakohtaiset tarkennukset", :leveys 8}
             {:otsikko "Valtakunnallinen takaraja", :leveys 5}
             {:otsikko "Takaraja urakassa", :leveys 5}
             {:otsikko "Tila", :leveys 5}
             {:otsikko "Valmistumispäivä", :leveys 5}
             {:otsikko "Kommentti valmistumisesta", :leveys 8}
             {:otsikko "Merkitsijä", :leveys 5}]
            nil]]
          true))))
