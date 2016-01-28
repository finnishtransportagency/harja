(ns harja.palvelin.raportointi-test
  (:require [harja.palvelin.raportointi :as r]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.palvelut.raportit :as raportit]
            [clojure.test :refer [deftest is testing] :as t]
            [clj-time.coerce :as c]
            [clj-time.core :as time]
            [harja.pvm :as pvm]))

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
                                    (r/luo-raportointi)
                                    [:db :pdf-vienti])
                      :raportit (component/using
                                  (raportit/->Raportit)
                                  [:http-palvelin :db :raportointi :pdf-vienti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(t/use-fixtures :each jarjestelma-fixture)

(deftest raporttien-haku-toimii
  (let [r (r/hae-raportit (:raportointi jarjestelma))]
    (is (contains? r :yks-hint-kuukausiraportti) "yks-hint-kuukausiraportti löytyy raporteista")
    (is (contains? r :erilliskustannukset) "Erilliskustannusraportti löytyy raporteista")
    (is (contains? r :tiestotarkastusraportti) "tiestotarkastusraportti löytyy raporteista")
    (is (contains? r :kelitarkastusraportti) "kelitarkastusraportti löytyy raporteista")
    (is (contains? r :ilmoitusraportti) "Ilmoitusraportti löytyy raporteista")
    (is (contains? r :turvallisuus) "turvallisuus löytyy raporteista")
    (is (contains? r :tyomaakokous) "tyomaakokous löytyy raporteista")
    (is (contains? r :suolasakko) "suolasakko löytyy raporteista")
    (is (contains? r :ymparistoraportti) "ymparistoraportti löytyy raporteista")
    (is (contains? r :laskutusyhteenveto) "Laskutusyhteenveto löytyy raporteista")
    (is (contains? r :materiaaliraportti) "Materiaaliraportti löytyy raporteista")
    (is (contains? r :yks-hint-tyot) "Yksikköhintaiset työt löytyy raporteista")
    (is (contains? r :yks-hint-tehtavien-summat) "yks-hint-tehtavien-summat löytyy raporteista")
    (is (not (contains? r :olematon-raportti)))))

(deftest kuukaudet-apuri-test
  (let [odotettu '("2014/10"
                   "2014/11"
                   "2014/12"
                   "2015/01"
                   "2015/02"
                   "2015/03"
                   "2015/04"
                   "2015/05"
                   "2015/06"
                   "2015/07"
                   "2015/08"
                   "2015/09")
        alkupvm (pvm/->pvm "1.10.2014")
        loppupvm (pvm/->pvm "30.9.2015")]
    (is (= odotettu (yleinen/kuukaudet alkupvm loppupvm))) "oikeat hoitokauden kuukaudet"))

(deftest raportin-suorituksen-oikeustarkistus
  (let [kutsu-palvelua (fn [kayttaja]
                         (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :suorita-raportti
                                         kayttaja
                                         {:nimi       :yks-hint-kuukausiraportti
                                          :konteksti  "koko maa"
                                          :parametrit {:alkupvm  (c/to-date (time/local-date 2005 10 10))
                                                       :loppupvm (c/to-date (time/local-date 2010 10 10))}}))]
    (is (thrown? Exception (kutsu-palvelua +kayttaja-yit_uuvh+)))
    ;; ei heitä virhettä järjestelmänvastuuhenkilölle
    (is (some? (kutsu-palvelua +kayttaja-jvh+)))
    ;; ei heitä virhettä tilaajan urakanvalvojalle
    (is (some? (kutsu-palvelua +kayttaja-tero+)))))
