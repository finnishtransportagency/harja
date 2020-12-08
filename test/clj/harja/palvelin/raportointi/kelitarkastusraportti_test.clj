(ns harja.palvelin.raportointi.kelitarkastusraportti-test
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
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.testiapurit :as apurit]))

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

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :kelitarkastusraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))}})]
    (is (vector? vastaus))
    (let [otsikko "Oulun alueurakka 2014-2019, Kelitarkastusraportti ajalta 01.10.2015 - 01.10.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Päivämäärä"}
                                          {:otsikko "Klo"}
                                          {:otsikko "Tie"}
                                          {:otsikko "Aosa"}
                                          {:otsikko "Aet"}
                                          {:otsikko "Losa"}
                                          {:otsikko "Let"}
                                          {:otsikko "Ajo\u00ADsuun\u00ADta"}
                                          {:otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
                                          {:otsikko "Lu\u00ADmi\u00ADmää\u00ADrä (cm)"}
                                          {:otsikko "Ta\u00ADsai\u00ADsuus (cm)"}
                                          {:otsikko "Kit\u00ADka"}
                                          {:otsikko "Ilman läm\u00ADpö\u00ADti\u00ADla"}
                                          {:otsikko "Tien läm\u00ADpö\u00ADti\u00ADla"}
                                          {:otsikko "Tar\u00ADkas\u00ADtaja"}
                                          {:otsikko "Ha\u00ADvain\u00ADnot"}
                                          {:otsikko "Laadun alitus"}
                                          {:otsikko "Liit\u00ADteet"}))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :kelitarkastusraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [otsikko "Pohjois-Pohjanmaa, Kelitarkastusraportti ajalta 01.10.2015 - 01.10.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Päivämäärä"}
                                          {:otsikko "Klo"}
                                          {:otsikko "Tie"}
                                          {:otsikko "Aosa"}
                                          {:otsikko "Aet"}
                                          {:otsikko "Losa"}
                                          {:otsikko "Let"}
                                          {:otsikko "Ajo\u00ADsuun\u00ADta"}
                                          {:otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
                                          {:otsikko "Lu\u00ADmi\u00ADmää\u00ADrä (cm)"}
                                          {:otsikko "Ta\u00ADsai\u00ADsuus (cm)"}
                                          {:otsikko "Kit\u00ADka"}
                                          {:otsikko "Ilman läm\u00ADpö\u00ADti\u00ADla"}
                                          {:otsikko "Tien läm\u00ADpö\u00ADti\u00ADla"}
                                          {:otsikko "Tar\u00ADkas\u00ADtaja"}
                                          {:otsikko "Ha\u00ADvain\u00ADnot"}
                                          {:otsikko "Laadun alitus"}
                                          {:otsikko "Liit\u00ADteet"}))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :kelitarkastusraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (let [otsikko "KOKO MAA, Kelitarkastusraportti ajalta 01.10.2015 - 01.10.2016"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Päivämäärä"}
                                          {:otsikko "Klo"}
                                          {:otsikko "Tie"}
                                          {:otsikko "Aosa"}
                                          {:otsikko "Aet"}
                                          {:otsikko "Losa"}
                                          {:otsikko "Let"}
                                          {:otsikko "Ajo\u00ADsuun\u00ADta"}
                                          {:otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}
                                          {:otsikko "Lu\u00ADmi\u00ADmää\u00ADrä (cm)"}
                                          {:otsikko "Ta\u00ADsai\u00ADsuus (cm)"}
                                          {:otsikko "Kit\u00ADka"}
                                          {:otsikko "Ilman läm\u00ADpö\u00ADti\u00ADla"}
                                          {:otsikko "Tien läm\u00ADpö\u00ADti\u00ADla"}
                                          {:otsikko "Tar\u00ADkas\u00ADtaja"}
                                          {:otsikko "Ha\u00ADvain\u00ADnot"}
                                          {:otsikko "Laadun alitus"}
                                          {:otsikko "Liit\u00ADteet"}))))