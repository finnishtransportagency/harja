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
  (let [generoitu-graylogista-luettu-data (gen/sample (s/gen ::graylog/csvsta-luettu-data) 1)
        data-kentat #{:pvm :kello :kayttaja :palvelut :viimeiset-katkokset}
        asetukset {:naytettavat-ryhmat (into #{} (random-sample 0.5 #{:hae :tallenna :urakka :muut}))
                   :min-katkokset (rand-int 5)}]
    ; (testing "Ilman optioita"
    ;   (doseq [data generoitu-graylogista-luettu-data]
    ;     (let [ryhma-avain (rand-nth (into '() data-kentat))
    ;           jarjestys-avain (rand-nth (into '() (disj data-kentat ryhma-avain)))
    ;           parsittu-data (graylog/parsi-yhteyskatkos-data data {:ryhma-avain ryhma-avain
    ;                                                                :jarjestys-avain jarjestys-avain})]
    ;       (is (s/valid? ::dgl/parsittu-yhteyskatkos-data parsittu-data)
    ;           (s/explain ::dgl/parsittu-yhteyskatkos-data parsittu-data)))))
    (testing "Ryhma-asetuksen kanssa"
      (doseq [data generoitu-graylogista-luettu-data]
        (let [ryhma-avain (rand-nth (into '() data-kentat))
              jarjestys-avain (rand-nth (into '() (disj data-kentat ryhma-avain)))
              parsittu-data (graylog/parsi-yhteyskatkos-data data (merge (dissoc asetukset :min-katkokset)
                                                                         {:ryhma-avain ryhma-avain
                                                                          :jarjestys-avain jarjestys-avain}))]
          (is (s/valid? ::dgl/parsittu-yhteyskatkos-data parsittu-data)
              (s/explain ::dgl/parsittu-yhteyskatkos-data parsittu-data)))))))
    ; (testing "min-katkokset-asetuksen kanssa"
    ;   (doseq [data generoitu-graylogista-luettu-data]
    ;     (let [ryhma-avain (rand-nth (into '() data-kentat))
    ;           jarjestys-avain (rand-nth (into '() (disj data-kentat ryhma-avain)))
    ;           parsittu-data (graylog/parsi-yhteyskatkos-data data (merge (dissoc asetukset :naytettavat-ryhmat)
    ;                                                                      {:ryhma-avain ryhma-avain
    ;                                                                       :jarjestys-avain jarjestys-avain}))]
    ;       (is (s/valid? ::dgl/parsittu-yhteyskatkos-data parsittu-data)
    ;           (s/explain ::dgl/parsittu-yhteyskatkos-data parsittu-data)))))))
