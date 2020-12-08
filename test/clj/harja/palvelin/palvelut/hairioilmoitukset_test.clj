(ns harja.palvelin.palvelut.hairioilmoitukset-test
  (:require [clojure.test :refer :all]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.hairioilmoitukset :as hairioilmoitukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.set :as set])
  (:import (java.util Date)))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hairioilmoitukset (component/using
                                             (hairioilmoitukset/->Hairioilmoitukset)
                                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest kaikki-saavat-hakea-tuoreimman-hairioilmoituksen
  (let [vastaus (kutsu-palvelua
                  (:http-palvelin jarjestelma)
                  :hae-tuorein-voimassaoleva-hairioilmoitus
                  +kayttaja-tero+
                  {})]
  (is (map? vastaus))
  (is (= (first (keys vastaus)) :hairioilmoitus))))