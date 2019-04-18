(ns harja.palvelin.raportointi.siltatarkastusraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
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

(deftest raportin-suoritus-sillalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:vuosi 2007
                                              :silta-id 1}})]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Siltatarkastusraportti")
    (let [otsikko "Siltatarkastusraportti, Oulun alueurakka 2005-2012, Oulujoen silta (O-00001), 2007"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-otsikko taulukko otsikko)
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "#"}
                                          {:otsikko "Kohde"}
                                          {:otsikko "Tulos"}
                                          {:otsikko "Lisätieto"}
                                          {:otsikko "Liitteet"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [rivi]
                                               (let [[numero kohde tulos lisatieto [_ liitteet] :as rivi]
                                                     (if (map? rivi)
                                                       (:rivi rivi)
                                                       rivi)]
                                                 (if rivi
                                                   (and (= (count rivi) 5)
                                                        (number? numero)
                                                        (string? kohde)
                                                        (if lisatieto (string? lisatieto)
                                                                      true)
                                                        (vector? liitteet))
                                                   ;; väliotsikkoriveille palautetaan elsestä true
                                                   true)))))))

(deftest raportin-suoritus-urakan-kaikille-silloille-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:vuosi 2007
                                              :silta-id :kaikki}})]

    (is (vector? vastaus))
    (is (= 5 (count vastaus)))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :siltatarkastus
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:vuosi 2007}})]
    (is (vector? vastaus))
    (apurit/tarkista-raportti vastaus "Siltatarkastusraportti")
    (let [otsikko "Siltatarkastusraportti, Pohjois-Pohjanmaa 2007"
          taulukko (apurit/taulukko-otsikolla vastaus otsikko)]
      (apurit/tarkista-taulukko-otsikko taulukko otsikko)
      (apurit/tarkista-taulukko-sarakkeet taulukko
                                          {:otsikko "Urakka"}
                                          {:otsikko "A"}
                                          {:otsikko "B"}
                                          {:otsikko "C"}
                                          {:otsikko "D"})
      (apurit/tarkista-taulukko-kaikki-rivit taulukko
                                             (fn [rivi]
                                               (let [[yhteensa a b c d :as rivi]
                                                     (if (map? rivi)
                                                       (:rivi rivi)
                                                       rivi)]
                                                 (and (= (count rivi) 5)
                                                      (string? yhteensa)
                                                      (keyword? (first a))
                                                      (keyword? (first b))
                                                      (keyword? (first c))
                                                      (keyword? (first d))
                                                      (map? (second a))
                                                      (map? (second b))
                                                      (map? (second c))
                                                      (map? (second d)))))))))
