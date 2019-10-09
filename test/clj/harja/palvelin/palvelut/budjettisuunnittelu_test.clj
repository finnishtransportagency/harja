(ns harja.palvelin.palvelut.budjettisuunnittelu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.budjettisuunnittelu :refer :all]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pois-kytketyt-ominaisuudet]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (luo-testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet (component/using
                                                      (pois-kytketyt-ominaisuudet/->PoisKytketytOminaisuudet #{})
                                                      [:http-palvelin])
                        :budjetoidut-tyot (component/using
                                            (->Budjettisuunnittelu)
                                            [:http-palvelin :db :pois-kytketyt-ominaisuudet])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))