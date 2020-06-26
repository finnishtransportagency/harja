(ns harja.palvelin.raportointi.ilmoitus-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all :as testi]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-lyhenne-ja-nimi +ilmoitustilat+]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi :refer :all]
            [harja.palvelin.raportointi.raportit.ilmoitus :as ilmoitusraportti]
            [harja.pvm :as pvm]
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

(deftest hae-ilmoitukset-raportille-koko-maa-test
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        [alkupvm loppupvm] (pvm/paivamaaran-hoitokausi (pvm/->pvm "1.11.2016"))
        ilmoitukset
        (ilmoitusraportti/hae-ilmoitukset-raportille
          db +kayttaja-jvh+ {:hallintayksikko-id  nil :urakka-id nil
                             :urakoitsija nil :urakkatyyppi :kaikki
                             :alkupvm alkupvm :loppupvm loppupvm})]
    (is (not (empty? ilmoitukset)))
    (is (= (count ilmoitukset) 7))))

(deftest hae-ilmoitukset-raportille-hoito-ja-teiden-hoito-test
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        odotettu [{:kuittaus {:kuittaustyyppi nil},
                   :sijainti nil,
                   :ilmoitustyyppi :kysely,
                   :urakka 26,
                   :hallintayksikko
                   {:id 12, :nimi "Pohjois-Pohjanmaa", :elynumero "12"},
                   :selitteet [],
                   :urakkatyyppi nil,
                   :ilmoittaja {:tyyppi nil}}
                  {:kuittaus {:kuittaustyyppi nil},
                   :sijainti nil,
                   :ilmoitustyyppi :toimenpidepyynto,
                   :urakka 31,
                   :hallintayksikko {:id 13, :nimi "Lappi", :elynumero "14"},
                   :selitteet [],
                   :urakkatyyppi nil,
                   :ilmoittaja {:tyyppi nil}}
                  {:kuittaus {:kuittaustyyppi nil},
                   :sijainti nil,
                   :ilmoitustyyppi :tiedoitus,
                   :urakka 31,
                   :hallintayksikko {:id 13, :nimi "Lappi", :elynumero "14"},
                   :selitteet [],
                   :urakkatyyppi nil,
                   :ilmoittaja {:tyyppi nil}}
                  {:kuittaus {:kuittaustyyppi nil},
                   :sijainti nil,
                   :ilmoitustyyppi :toimenpidepyynto,
                   :urakka 31,
                   :hallintayksikko {:id 13, :nimi "Lappi", :elynumero "14"},
                   :selitteet [],
                   :urakkatyyppi nil,
                   :ilmoittaja {:tyyppi nil}}
                  {:kuittaus {:kuittaustyyppi nil},
                   :sijainti nil,
                   :ilmoitustyyppi :tiedoitus,
                   :urakka 31,
                   :hallintayksikko {:id 13, :nimi "Lappi", :elynumero "14"},
                   :selitteet [],
                   :urakkatyyppi nil,
                   :ilmoittaja {:tyyppi nil}}
                  {:kuittaus {:kuittaustyyppi nil},
                   :sijainti nil,
                   :ilmoitustyyppi :tiedoitus,
                   :urakka 31,
                   :hallintayksikko {:id 13, :nimi "Lappi", :elynumero "14"},
                   :selitteet [],
                   :urakkatyyppi nil,
                   :ilmoittaja {:tyyppi nil}}
                  {:kuittaus {:kuittaustyyppi nil},
                   :sijainti nil,
                   :ilmoitustyyppi :toimenpidepyynto,
                   :urakka 31,
                   :hallintayksikko {:id 13, :nimi "Lappi", :elynumero "14"},
                   :selitteet [],
                   :urakkatyyppi nil,
                   :ilmoittaja {:tyyppi nil}}]
        [alkupvm loppupvm] (pvm/paivamaaran-hoitokausi (pvm/nyt))
        ilmoitukset
        (ilmoitusraportti/hae-ilmoitukset-raportille
          db +kayttaja-jvh+ {:hallintayksikko-id  nil :urakka-id nil
                             :urakoitsija nil :urakkatyyppi :hoito
                             :alkupvm alkupvm :loppupvm loppupvm})]
    (is (= odotettu (map #(dissoc % :ilmoitettu) ilmoitukset)))
    (is (not (empty? ilmoitukset)))
    (is (= (count ilmoitukset) 7))))

(deftest hae-ilmoitukset-raportille-hoito-ja-teiden-hoito-laaja-aikavali-test
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        [alkupvm loppupvm] [(pvm/->pvm "1.1.2015") (pvm/nyt)]
        ilmoitukset
        (ilmoitusraportti/hae-ilmoitukset-raportille
          db +kayttaja-jvh+ {:hallintayksikko-id  nil :urakka-id nil
                             :urakoitsija nil :urakkatyyppi :hoito
                             :alkupvm alkupvm :loppupvm loppupvm})]
    (is (not (empty? ilmoitukset)))
    (is (= (count ilmoitukset) 32))))


(deftest hae-ilmoitukset-raportille-organisaatio-seka-urakka-id-test
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        [alkupvm loppupvm] (pvm/paivamaaran-hoitokausi (pvm/nyt))
        rovaniemen-urakan-id (testi/hae-rovaniemen-maanteiden-hoitourakan-id)
        lapin-elyn-id (->
                        (testi/q "SELECT id FROM organisaatio WHERE nimi = 'Lappi'")
                        ffirst)
        ilmoitukset
        (ilmoitusraportti/hae-ilmoitukset-raportille
          db +kayttaja-jvh+ {:hallintayksikko-id lapin-elyn-id :urakka-id rovaniemen-urakan-id
                             :urakoitsija nil :urakkatyyppi :kaikki
                             :alkupvm alkupvm :loppupvm loppupvm})]
    (is (not (empty? ilmoitukset)))
    (is (= (count ilmoitukset) 6))))

(deftest hae-ilmoitukset-raportille-pelkka-organisaatio-test
  (let [db (tietokanta/luo-tietokanta testitietokanta)
        [alkupvm loppupvm] [(pvm/->pvm "1.1.2015") (pvm/nyt)]
        elyn-id (->
                  (testi/q "SELECT id FROM organisaatio WHERE nimi = 'Pohjois-Pohjanmaa'")
                  ffirst)
        ilmoitukset
        (ilmoitusraportti/hae-ilmoitukset-raportille
          db +kayttaja-jvh+ {:hallintayksikko-id elyn-id :urakka-id nil
                             :urakoitsija nil :urakkatyyppi :kaikki
                             :alkupvm alkupvm :loppupvm loppupvm})]
    (is (not (empty? ilmoitukset)))
    (is (= (count ilmoitukset) 26))))