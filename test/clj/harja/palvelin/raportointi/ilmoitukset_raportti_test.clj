(ns harja.palvelin.raportointi.ilmoitukset-raportti-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.ilmoitukset :as ilmoitukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.http-palvelin :as palvelin]
            [harja.palvelin.raportointi :as raportointi :refer [suorita-raportti]]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]))

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

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest ilmoitukset-raportti-toimii
  (palvelin/julkaise-palvelu (:http-palvelin jarjestelma) :suorita-raportti
                             (fn [user raportti]
                               (suorita-raportti (:raportointi jarjestelma) user raportti))
                             {:trace false})

  (let [tiedot {:nimi :ilmoitukset-raportti
                :konteksti "urakka"
                :alkupvm (c/to-date (t/local-date 2023 3 28))}

        raportti (ilmoitukset/suorita (:db jarjestelma)
                                      +kayttaja-jvh+
                                      tiedot)

        raportin-nimi (-> raportti second :nimi)]

    ;; Raportti ei sisällä dataa, data annetaan parametreina 
    (is (= raportin-nimi "Ilmoitukset, Koko maa"))))
