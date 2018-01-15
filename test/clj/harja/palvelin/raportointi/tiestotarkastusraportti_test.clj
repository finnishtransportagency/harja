(ns harja.palvelin.raportointi.tiestotarkastusraportti-test
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

(deftest raportin-suoritus-urakalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :tiestotarkastusraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Tiestötarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2014-2019, Tiestötarkastusraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Tiestötarkastusraportti"
                      :tyhja nil}
                     [{:leveys 10
                       :otsikko "Päi­vä­mää­rä"}
                       {:leveys 5
                        :otsikko "Klo"}
                       {:leveys 6
                        :otsikko "Tie"}
                       {:leveys 6
                        :otsikko "Aosa"}
                       {:leveys 6
                        :otsikko "Aet"}
                       {:leveys 6
                        :otsikko "Losa"}
                       {:leveys 6
                        :otsikko "Let"}
                       {:leveys 20
                        :otsikko "Tar­kas­taja"}
                       {:leveys 25
                        :otsikko "Ha­vain­not"}
                       {:leveys 6
                        :otsikko "Laadun alitus"}
                       {:leveys 5
                        :otsikko "Liit­teet"
                        :tyyppi :liite}]
                     [["05.01.2015"
                       "16:18"
                       4
                       364
                       8012
                       nil
                       nil
                       "Tarmo Tarkastaja"
                       "Tiessä oli muutamia pieniä kuoppia, ei kuitenkaan mitään vakavaa ongelmaa aiheuta"
                       "Ei"
                       [:liitteet
                        []]]
                      ["02.01.2015"
                       "16:02"
                       4
                       364
                       8012
                       nil
                       nil
                       "Tarmo Tarkastaja"
                       "Tiessä oli pieni kuoppa"
                       "Ei"
                       [:liitteet
                        [{:id 2
                          :koko nil
                          :nimi "tiesto5667858.jpg"
                          :oid nil
                          :tyyppi "image/jpeg"}
                         {:id 1
                          :koko nil
                          :nimi "tiestotarkastus_456.jpg"
                          :oid nil
                          :tyyppi "image/jpeg"}]]]]]]))))


(deftest raportin-suoritus-hallintayksikolle-toimii-usean-vuoden-aikavalilla
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :tiestotarkastusraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 10 1))
                                              :loppupvm (c/to-date (t/local-date 2015 10 1))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Tiestötarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Pohjois-Pohjanmaa, Tiestötarkastusraportti ajalta 01.10.2014 - 01.10.2015"
                      :sheet-nimi "Tiestötarkastusraportti"
                      :tyhja nil}
                     [{:leveys 10
                       :otsikko "Päi­vä­mää­rä"}
                       {:leveys 5
                        :otsikko "Klo"}
                       {:leveys 6
                        :otsikko "Tie"}
                       {:leveys 6
                        :otsikko "Aosa"}
                       {:leveys 6
                        :otsikko "Aet"}
                       {:leveys 6
                        :otsikko "Losa"}
                       {:leveys 6
                        :otsikko "Let"}
                       {:leveys 20
                        :otsikko "Tar­kas­taja"}
                       {:leveys 25
                        :otsikko "Ha­vain­not"}
                       {:leveys 6
                        :otsikko "Laadun alitus"}
                       {:leveys 5
                        :otsikko "Liit­teet"
                        :tyyppi :liite}]
                     [{:otsikko "Oulun alueurakka 2014-2019"}
                      ["05.01.2015"
                       "16:18"
                       4
                       364
                       8012
                       nil
                       nil
                       "Tarmo Tarkastaja"
                       "Tiessä oli muutamia pieniä kuoppia, ei kuitenkaan mitään vakavaa ongelmaa aiheuta"
                       "Ei"
                       [:liitteet
                        []]]
                      ["02.01.2015"
                       "16:02"
                       4
                       364
                       8012
                       nil
                       nil
                       "Tarmo Tarkastaja"
                       "Tiessä oli pieni kuoppa"
                       "Ei"
                       [:liitteet
                        [{:id 2
                          :koko nil
                          :nimi "tiesto5667858.jpg"
                          :oid nil
                          :tyyppi "image/jpeg"}
                         {:id 1
                          :koko nil
                          :nimi "tiestotarkastus_456.jpg"
                          :oid nil
                          :tyyppi "image/jpeg"}]]]]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :tiestotarkastusraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2014 1 1))
                                              :loppupvm (c/to-date (t/local-date 2015 12 31))
                                              :urakkatyyppi :hoito}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Tiestötarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "KOKO MAA, Tiestötarkastusraportti ajalta 01.01.2014 - 31.12.2015"
                      :sheet-nimi "Tiestötarkastusraportti"
                      :tyhja nil}
                     [{:leveys 10
                       :otsikko "Päi­vä­mää­rä"}
                       {:leveys 5
                        :otsikko "Klo"}
                       {:leveys 6
                        :otsikko "Tie"}
                       {:leveys 6
                        :otsikko "Aosa"}
                       {:leveys 6
                        :otsikko "Aet"}
                       {:leveys 6
                        :otsikko "Losa"}
                       {:leveys 6
                        :otsikko "Let"}
                       {:leveys 20
                        :otsikko "Tar­kas­taja"}
                       {:leveys 25
                        :otsikko "Ha­vain­not"}
                       {:leveys 6
                        :otsikko "Laadun alitus"}
                       {:leveys 5
                        :otsikko "Liit­teet"
                        :tyyppi :liite}]
                     [{:otsikko "Oulun alueurakka 2014-2019"}
                      ["05.01.2015"
                       "16:18"
                       4
                       364
                       8012
                       nil
                       nil
                       "Tarmo Tarkastaja"
                       "Tiessä oli muutamia pieniä kuoppia, ei kuitenkaan mitään vakavaa ongelmaa aiheuta"
                       "Ei"
                       [:liitteet
                        []]]
                      ["02.01.2015"
                       "16:02"
                       4
                       364
                       8012
                       nil
                       nil
                       "Tarmo Tarkastaja"
                       "Tiessä oli pieni kuoppa"
                       "Ei"
                       [:liitteet
                        [{:id 2
                          :koko nil
                          :nimi "tiesto5667858.jpg"
                          :oid nil
                          :tyyppi "image/jpeg"}
                         {:id 1
                          :koko nil
                          :nimi "tiestotarkastus_456.jpg"
                          :oid nil
                          :tyyppi "image/jpeg"}]]]]]]))))
