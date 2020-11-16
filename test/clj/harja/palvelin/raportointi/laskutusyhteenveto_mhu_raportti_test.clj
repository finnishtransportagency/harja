(ns harja.palvelin.raportointi.laskutusyhteenveto-mhu-raportti-test
  (:require [clojure.test :refer :all]
            [org.httpkit.client :as http]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.data.json :as json]
            [harja.palvelin.index :as index]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as palvelin]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]
            [harja.oikea-http-palvelin-jarjestelma-fixture :as op]))


(def jarjestelma-fixture
  (op/luo-fixture
    :pdf-vienti (component/using
                  (pdf-vienti/luo-pdf-vienti)
                  [:http-palvelin])
    :excel-vienti (component/using
                    (excel-vienti/luo-excel-vienti)
                    [:http-palvelin])
    :raportointi (component/using
                   (raportointi/luo-raportointi)
                   [:db :pdf-vienti])
    :raportit (component/using
                (raportit/->Raportit)
                [:http-palvelin :db :db-replica :raportointi :pdf-vienti])
    :db-replica (tietokanta/luo-tietokanta testitietokanta true)))

(use-fixtures :each (compose-fixtures
                      urakkatieto-fixture
                      jarjestelma-fixture))

(defn- arvo-raportin-nnesta-elementista [vastaus n]
  (second (first (second (second (last (nth (nth (last vastaus) n) 3)))))))

(deftest raportti-toimii
  (palvelin/julkaise-palvelu (:http-palvelin jarjestelma) :suorita-raportti
                             (fn [user raportti]
                               (suorita-raportti (:raportointi jarjestelma) user raportti))
                             {:trace false})
  (let [vastaus (op/post-kutsu
                  :suorita-raportti
                  {:nimi       :laskutusyhteenveto-mhu
                   :konteksti  "urakka"
                   :urakka-id  (hae-oulun-alueurakan-2014-2019-id)
                   :parametrit {:urakkatyyppi :teiden-hoito
                                :alkupvm      (c/to-date (t/local-date 2014 10 1))
                                :loppupvm     (c/to-date (t/local-date 2015 9 30))}})]
    (is (= 200 (:status vastaus)))))

