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
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.palvelin.raportointi :refer [suorita-raportti]]))

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
  (try
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

          raportin-nimi (-> raportti second :nimi)
          taulukon-otsikko (-> raportti (nth 2) second (nth 2) first :otsikko)]
      
      ;; Raportti ei sisällä dataa, data annetaan parametreina 
      (is (= taulukon-otsikko "Käytetyt filtterit"))
      (is (= raportin-nimi "Ilmoitukset, Koko maa")))

    (catch Throwable t
      (log/error t "Virhe testissä ilmoitukset-raportti-toimii"))))
