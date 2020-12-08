(ns harja.palvelin.raportointi.laaduntarkastusraportti-test
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
                                {:nimi :laaduntarkastusraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2005-2012-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 10 1))
                                              :loppupvm (c/to-date (t/local-date 2006 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Laaduntarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2005-2012, Laaduntarkastusraportti ajalta 01.10.2005 - 01.10.2006"
                      :sheet-nimi "Laaduntarkastusraportti"
                      :tyhja nil}
                     [{:leveys 4
                       :otsikko "Päivämäärä"}
                      {:leveys 2
                       :otsikko "Klo"}
                      {:leveys 2
                       :otsikko "Tie"}
                      {:leveys 2
                       :otsikko "Aosa"}
                      {:leveys 2
                       :otsikko "Aet"}
                      {:leveys 2
                       :otsikko "Losa"}
                      {:leveys 2
                       :otsikko "Let"}
                      {:leveys 3
                       :otsikko "Tar­kas­taja"}
                      {:leveys 8
                       :otsikko "Mittaus"}
                      {:leveys 10
                       :otsikko "Ha­vain­not"}
                      {:leveys 2
                       :otsikko "Laadun alitus"}
                      {:leveys 3
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     [{:otsikko "Oulun alueurakka 2005-2012"}
                      ["03.10.2005"
                       "00:10"
                       1
                       2
                       3
                       3
                       4
                       "Matti"
                       ""
                       "havaittiin kaikenlaista"
                       "Ei"
                       [:liitteet
                        []]]]]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :laaduntarkastusraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (ffirst (q (str "SELECT id FROM organisaatio WHERE nimi = 'Uusimaa'")))
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 10 1))
                                              :loppupvm (c/to-date (t/local-date 2006 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Laaduntarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Uusimaa, Laaduntarkastusraportti ajalta 01.10.2005 - 01.10.2006"
                      :sheet-nimi "Laaduntarkastusraportti"
                      :tyhja "Ei raportoitavia tarkastuksia."}
                     [{:leveys 4
                       :otsikko "Päivämäärä"}
                      {:leveys 2
                       :otsikko "Klo"}
                      {:leveys 2
                       :otsikko "Tie"}
                      {:leveys 2
                       :otsikko "Aosa"}
                      {:leveys 2
                       :otsikko "Aet"}
                      {:leveys 2
                       :otsikko "Losa"}
                      {:leveys 2
                       :otsikko "Let"}
                      {:leveys 3
                       :otsikko "Tar­kas­taja"}
                      {:leveys 8
                       :otsikko "Mittaus"}
                      {:leveys 10
                       :otsikko "Ha­vain­not"}
                      {:leveys 2
                       :otsikko "Laadun alitus"}
                      {:leveys 3
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     []]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :laaduntarkastusraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2005 10 1))
                                              :loppupvm (c/to-date (t/local-date 2006 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Laaduntarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "KOKO MAA, Laaduntarkastusraportti ajalta 01.10.2005 - 01.10.2006"
                      :sheet-nimi "Laaduntarkastusraportti"
                      :tyhja nil}
                     [{:leveys 4
                       :otsikko "Päivämäärä"}
                      {:leveys 2
                       :otsikko "Klo"}
                      {:leveys 2
                       :otsikko "Tie"}
                      {:leveys 2
                       :otsikko "Aosa"}
                      {:leveys 2
                       :otsikko "Aet"}
                      {:leveys 2
                       :otsikko "Losa"}
                      {:leveys 2
                       :otsikko "Let"}
                      {:leveys 3
                       :otsikko "Tar­kas­taja"}
                      {:leveys 8
                       :otsikko "Mittaus"}
                      {:leveys 10
                       :otsikko "Ha­vain­not"}
                      {:leveys 2
                       :otsikko "Laadun alitus"}
                      {:leveys 3
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     [{:otsikko "Oulun alueurakka 2005-2012"}
                      ["03.10.2005"
                       "00:10"
                       1
                       2
                       3
                       3
                       4
                       "Matti"
                       ""
                       "havaittiin kaikenlaista"
                       "Ei"
                       [:liitteet
                        []]]]]]))))