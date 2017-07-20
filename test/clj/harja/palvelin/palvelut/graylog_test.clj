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

(deftest parsi-yhteyskatkos-data
  (let [generoitu-graylogista-luettu-data (gen/sample (s/gen ::graylog/csvsta-luettu-data))
        asetukset {:pvm? true :kello? true :kayttaja? true :palvelut? true
                   :ensimmaiset-katkokset? true :viimeiset-katkokset? true}]
    (testing "Ilman optioita"
      (doseq [data generoitu-graylogista-luettu-data]
        (let [parsittu-data (graylog/parsi-yhteyskatkos-data data asetukset)]
          (is (s/valid? ::dgl/parsittu-yhteyskatkos-data parsittu-data)
              (s/explain ::dgl/parsittu-yhteyskatkos-data parsittu-data)))))
    (testing "check-homma"
      (stest/check `graylog/parsi-yhteyskatkos-data))))
