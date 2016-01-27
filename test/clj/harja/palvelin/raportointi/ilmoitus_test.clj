(ns harja.palvelin.raportointi.ilmoitus-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.domain.ilmoitusapurit :refer [+ilmoitustyypit+ ilmoitustyypin-lyhenne-ja-nimi +ilmoitustilat+]]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi :refer :all]
            [harja.palvelin.raportointi.raportit.ilmoitus :as ilmoitusraportti]
            [harja.pvm :as pvm]
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

(deftest hae-ilmoitukset-raportille-test
  ;; parametrit: db user hallintayksikko-id urakka-id urakoitsija urakkatyyppi
  ;; +ilmoitustilat+ +ilmoitustyypit+ [alkupvm loppupvm] hakuehto
    (let [db (tietokanta/luo-tietokanta testitietokanta)
          [alkupvm loppupvm] (pvm/paivamaaran-hoitokausi (pvm/->pvm "1.11.2014"))
          ilmoitukset (ilmoitusraportti/hae-ilmoitukset-raportille
                  db +kayttaja-jvh+ nil nil nil nil
                  +ilmoitustilat+ +ilmoitustyypit+
                  [alkupvm loppupvm] "")
          ristisuon-ilmoitus (first (filter #(= (:lyhytselite %) "Voimakas lumipyry nelostiellä Ristisuon kohdalla ja tiet auraamatta.")
                                      ilmoitukset))]
      (is (= (pvm/pvm (:ilmoitettu ristisuon-ilmoitus)) "26.01.2015"))
      (is (not (empty? ilmoitukset)))
      (is (> (count ilmoitukset) 2))))
