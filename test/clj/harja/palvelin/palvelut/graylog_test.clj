(ns harja.palvelin.palvelut.graylog-test
  (:require [harja.palvelin.palvelut.graylog :as graylog]
            [harja.domain.graylog :as dgl]
            [clojure.test :as t :refer [deftest is testing]]
            [harja.testi :as testi]
            [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'testi/jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :http-palvelin (testi/testi-http-palvelin)))))

  (testit)
  (alter-var-root #'testi/jarjestelma component/stop))

(t/use-fixtures :each jarjestelma-fixture)

(defn generoidun-spekin-tarkistus
  [otos spekki funktio]
  (doseq [havainto otos]
    (is (s/valid? spekki (funktio havainto))
        (s/explain spekki (funktio havainto)))))
(defn rand-avaimet
  []
  (let [yhteyskatkos-kentat #{:pvm :kello :kayttaja :palvelut :ensimmaiset-katkokset :viimeiset-katkokset}
        ryhma-avain (rand-nth (into '() yhteyskatkos-kentat))
        jarjestys-avain (rand-nth (into '() (disj yhteyskatkos-kentat ryhma-avain)))]
    #{ryhma-avain jarjestys-avain}))

(deftest parsi-yhteyskatkos-data
  (testing "yhteyskatkokset-lokitus-string->yhteyskatkokset-map funktion testaus"
    (generoidun-spekin-tarkistus (gen/sample (s/gen ::graylog/graylogista-luettu-itemi) 20)
                                 ::dgl/yhteyskatkokset-lokitus-mappina
                                 (fn [havainto]
                                    (graylog/yhteyskatkokset-lokitus-string->yhteyskatkokset-map havainto
                                                                                                 (rand-avaimet))))))
