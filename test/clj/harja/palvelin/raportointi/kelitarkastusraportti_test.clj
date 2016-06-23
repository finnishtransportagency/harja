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
                                {:nimi :kelitarkastusraportti
                                 :konteksti "urakka"
                                 :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Kelitarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Oulun alueurakka 2014-2019, Kelitarkastusraportti ajalta 01.10.2015 - 01.10.2016"
                      :sheet-nimi "Kelitarkastusraportti"
                      :tyhja nil}
                     [{:leveys 10
                       :otsikko "Päivämäärä"}
                      {:leveys 5
                       :otsikko "Klo"}
                      {:leveys 5
                       :otsikko "Tie"}
                      {:leveys 5
                       :otsikko "Aosa"}
                      {:leveys 5
                       :otsikko "Aet"}
                      {:leveys 5
                       :otsikko "Losa"}
                      {:leveys 5
                       :otsikko "Let"}
                      {:leveys 5
                       :otsikko "Ajo­suun­ta"}
                      {:leveys 5
                       :otsikko "Hoi­to­luok­ka"}
                      {:leveys 5
                       :otsikko "Lu­mi­mää­rä (cm)"}
                      {:leveys 5
                       :otsikko "E­pä­ta­sai­suus (cm)"}
                      {:leveys 5
                       :otsikko "Kit­ka"}
                      {:leveys 5
                       :otsikko "Ilman läm­pö­ti­la"}
                      {:leveys 5
                       :otsikko "Tien läm­pö­ti­la"}
                      {:leveys 5
                       :otsikko "Tar­kas­taja"}
                      {:leveys 10
                       :otsikko "Ha­vain­not"}
                      {:leveys 3
                       :otsikko "Laadun alitus"}
                      {:leveys 5
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     [["04.01.2016"
                       "06:02"
                       4
                       364
                       5
                       nil
                       nil
                       1
                       "A"
                       "10,00"
                       "6,00"
                       "0,40"
                       "-13,00"
                       "-6,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 2"
                       "Ei"
                       [:liitteet
                        []]]
                      ["02.01.2016"
                       "16:02"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "A"
                       "10,00"
                       "5,00"
                       "1,00"
                       "-16,00"
                       "-3,00"
                       "Matti"
                       "Urakoitsija on kirjannut tämän tarkastuksen Harjaan käsin"
                       "Ei"
                       [:liitteet
                        []]]
                      ["28.12.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "11,00"
                       "5,00"
                       "0,10"
                       "-14,00"
                       "-6,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 1"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.11.2015"
                       "11:00"
                       4
                       364
                       8012
                       nil
                       nil
                       2
                       "A"
                       "9,00"
                       "6,00"
                       "0,30"
                       "-13,00"
                       "-3,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 3"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "ok"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       ""
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "Ok"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 4"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "OK"
                       "Ei"
                       [:liitteet
                        []]]]]]))))

(deftest raportin-suoritus-hallintayksikolle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :kelitarkastusraportti
                                 :konteksti "hallintayksikko"
                                 :hallintayksikko-id (hae-pohjois-pohjanmaan-hallintayksikon-id)
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Kelitarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "Pohjois-Pohjanmaa, Kelitarkastusraportti ajalta 01.10.2015 - 01.10.2016"
                      :sheet-nimi "Kelitarkastusraportti"
                      :tyhja nil}
                     [{:leveys 10
                       :otsikko "Päivämäärä"}
                      {:leveys 5
                       :otsikko "Klo"}
                      {:leveys 5
                       :otsikko "Tie"}
                      {:leveys 5
                       :otsikko "Aosa"}
                      {:leveys 5
                       :otsikko "Aet"}
                      {:leveys 5
                       :otsikko "Losa"}
                      {:leveys 5
                       :otsikko "Let"}
                      {:leveys 5
                       :otsikko "Ajo­suun­ta"}
                      {:leveys 5
                       :otsikko "Hoi­to­luok­ka"}
                      {:leveys 5
                       :otsikko "Lu­mi­mää­rä (cm)"}
                      {:leveys 5
                       :otsikko "E­pä­ta­sai­suus (cm)"}
                      {:leveys 5
                       :otsikko "Kit­ka"}
                      {:leveys 5
                       :otsikko "Ilman läm­pö­ti­la"}
                      {:leveys 5
                       :otsikko "Tien läm­pö­ti­la"}
                      {:leveys 5
                       :otsikko "Tar­kas­taja"}
                      {:leveys 10
                       :otsikko "Ha­vain­not"}
                      {:leveys 3
                       :otsikko "Laadun alitus"}
                      {:leveys 5
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     [{:otsikko "Oulun alueurakka 2014-2019"}
                      ["04.01.2016"
                       "06:02"
                       4
                       364
                       5
                       nil
                       nil
                       1
                       "A"
                       "10,00"
                       "6,00"
                       "0,40"
                       "-13,00"
                       "-6,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 2"
                       "Ei"
                       [:liitteet
                        []]]
                      ["02.01.2016"
                       "16:02"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "A"
                       "10,00"
                       "5,00"
                       "1,00"
                       "-16,00"
                       "-3,00"
                       "Matti"
                       "Urakoitsija on kirjannut tämän tarkastuksen Harjaan käsin"
                       "Ei"
                       [:liitteet
                        []]]
                      ["28.12.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "11,00"
                       "5,00"
                       "0,10"
                       "-14,00"
                       "-6,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 1"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.11.2015"
                       "11:00"
                       4
                       364
                       8012
                       nil
                       nil
                       2
                       "A"
                       "9,00"
                       "6,00"
                       "0,30"
                       "-13,00"
                       "-3,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 3"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "ok"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       ""
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "Ok"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 4"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "OK"
                       "Ei"
                       [:liitteet
                        []]]]]]))))

(deftest raportin-suoritus-koko-maalle-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :kelitarkastusraportti
                                 :konteksti "koko maa"
                                 :parametrit {:alkupvm (c/to-date (t/local-date 2015 10 1))
                                              :loppupvm (c/to-date (t/local-date 2016 10 1))
                                              :urakkatyyppi "hoito"}})]
    (is (vector? vastaus))
    (is (= vastaus [:raportti
                    {:nimi "Kelitarkastusraportti"
                     :orientaatio :landscape}
                    [:taulukko
                     {:otsikko "KOKO MAA, Kelitarkastusraportti ajalta 01.10.2015 - 01.10.2016"
                      :sheet-nimi "Kelitarkastusraportti"
                      :tyhja nil}
                     [{:leveys 10
                       :otsikko "Päivämäärä"}
                      {:leveys 5
                       :otsikko "Klo"}
                      {:leveys 5
                       :otsikko "Tie"}
                      {:leveys 5
                       :otsikko "Aosa"}
                      {:leveys 5
                       :otsikko "Aet"}
                      {:leveys 5
                       :otsikko "Losa"}
                      {:leveys 5
                       :otsikko "Let"}
                      {:leveys 5
                       :otsikko "Ajo­suun­ta"}
                      {:leveys 5
                       :otsikko "Hoi­to­luok­ka"}
                      {:leveys 5
                       :otsikko "Lu­mi­mää­rä (cm)"}
                      {:leveys 5
                       :otsikko "E­pä­ta­sai­suus (cm)"}
                      {:leveys 5
                       :otsikko "Kit­ka"}
                      {:leveys 5
                       :otsikko "Ilman läm­pö­ti­la"}
                      {:leveys 5
                       :otsikko "Tien läm­pö­ti­la"}
                      {:leveys 5
                       :otsikko "Tar­kas­taja"}
                      {:leveys 10
                       :otsikko "Ha­vain­not"}
                      {:leveys 3
                       :otsikko "Laadun alitus"}
                      {:leveys 5
                       :otsikko "Liit­teet"
                       :tyyppi :liite}]
                     [{:otsikko "Oulun alueurakka 2014-2019"}
                      ["04.01.2016"
                       "06:02"
                       4
                       364
                       5
                       nil
                       nil
                       1
                       "A"
                       "10,00"
                       "6,00"
                       "0,40"
                       "-13,00"
                       "-6,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 2"
                       "Ei"
                       [:liitteet
                        []]]
                      ["02.01.2016"
                       "16:02"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "A"
                       "10,00"
                       "5,00"
                       "1,00"
                       "-16,00"
                       "-3,00"
                       "Matti"
                       "Urakoitsija on kirjannut tämän tarkastuksen Harjaan käsin"
                       "Ei"
                       [:liitteet
                        []]]
                      ["28.12.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "11,00"
                       "5,00"
                       "0,10"
                       "-14,00"
                       "-6,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 1"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.11.2015"
                       "11:00"
                       4
                       364
                       8012
                       nil
                       nil
                       2
                       "A"
                       "9,00"
                       "6,00"
                       "0,30"
                       "-13,00"
                       "-3,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 3"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "ok"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       ""
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "Ok"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "järjestelmän raportoima testitarkastus 4"
                       "Ei"
                       [:liitteet
                        []]]
                      ["23.10.2015"
                       "10:00"
                       4
                       364
                       8012
                       nil
                       nil
                       1
                       "B"
                       "6,00"
                       "6,00"
                       "0,50"
                       "-15,00"
                       "-5,00"
                       "Matti"
                       "OK"
                       "Ei"
                       [:liitteet
                        []]]]]]))))