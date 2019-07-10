(ns harja.palvelin.palvelut.budjettisuunnittelu-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]

            [harja.kyselyt.urakat :as urk-q]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.budjettisuunnittelu :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                     (component/system-map
                      :db (luo-testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :budjetoidut-tyot (component/using
                                  (->Budjettisuunnittelu)
                                  [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))



(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

