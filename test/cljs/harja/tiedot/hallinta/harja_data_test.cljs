(ns harja.tiedot.hallinta.harja-data-test
  (:require [cljs.test :refer [deftest testing is]]
            [harja.tiedot.hallinta.harja-data :as tiedot]
            [harja.domain.graylog :as dgl]
            [cljs.spec.alpha :as s]
            [cljs.spec.gen.alpha :as gen]
            [cljs.spec.test.alpha :as stest]
            [clojure.test.check.generators]
            [clojure.set :as set-math]))
(defn satunnainen-set-elementti
  [setti]
  (rand-nth (into '() setti)))

(defn generoidun-spekin-tarkistus
  [otos spekki funktio]
  (doseq [havainto otos]
    (is (s/valid? spekki (funktio havainto))
        (s/explain spekki (funktio havainto)))))

(deftest tarkista-jarjestetty-yhteyskatkos-data
  (let [graylogista-parsittu-data-sample (gen/sample (s/gen ::dgl/parsittu-yhteyskatkos-data) 20)
        mahdollisesti-parsittavat-vektori-arvot #{:palvelut :ensimmaiset-katkokset :viimeiset-katkokset}
        mahdollisesti-parsittavat-ei-vektori-arvot #{:pvm :kello :kayttaja}
        mahdollisesti-parsittavat-arvot (set-math/union mahdollisesti-parsittavat-vektori-arvot
                                                        mahdollisesti-parsittavat-ei-vektori-arvot)
        tarkistus-funktio (partial generoidun-spekin-tarkistus graylogista-parsittu-data-sample ::tiedot/jarjestetty-yhteyskatkos-data)]
    (testing "yhteyskatkosdatan järjestely ryhmäavaimena vektori"
      (tarkistus-funktio #(let [jarjestys-avain (satunnainen-set-elementti mahdollisesti-parsittavat-arvot)
                                ryhma-avain (satunnainen-set-elementti mahdollisesti-parsittavat-vektori-arvot)]
                              (tiedot/jarjestele-yhteyskatkos-data jarjestys-avain ryhma-avain %))))
    (testing "yhteyskatkosdatan järjestely ryhmäavaimena joku muu kuin vektori"
      (tarkistus-funktio #(let [jarjestys-avain (satunnainen-set-elementti mahdollisesti-parsittavat-arvot)
                                ryhma-avain (satunnainen-set-elementti mahdollisesti-parsittavat-ei-vektori-arvot)]
                            (tiedot/jarjestele-yhteyskatkos-data jarjestys-avain ryhma-avain %))))
    (testing "yhteyskatkosdatan järjestely järjestysavaimena vektroi"
      (tarkistus-funktio #(let [jarjestys-avain (satunnainen-set-elementti mahdollisesti-parsittavat-vektori-arvot)
                                ryhma-avain (satunnainen-set-elementti mahdollisesti-parsittavat-arvot)]
                            (tiedot/jarjestele-yhteyskatkos-data jarjestys-avain ryhma-avain %))))
    (testing "yhteyskatkosdatan järjestely järjestysavaimena joku muu kuin vektori"
      (tarkistus-funktio #(let [jarjestys-avain (satunnainen-set-elementti mahdollisesti-parsittavat-ei-vektori-arvot)
                                ryhma-avain (satunnainen-set-elementti mahdollisesti-parsittavat-arvot)]
                            (tiedot/jarjestele-yhteyskatkos-data jarjestys-avain ryhma-avain %))))))
