(ns harja.tiedot.hallinta.harja-data-test
  (:require [clojure.test :refer [deftest testing is]]
            [harja.tiedot.hallinta.harja-data :as tiedot]
            [harja.domain.graylog :as dgl]
            [cljs.spec.alpha :as s]
            [cljs.spec.gen.alpha :as gen]
            [clojure.set :as set-math]))
(defn satunnainen-set-elementti
  [setti]
  (rand-nth (into '() setti)))
(deftest tarkista-jarjestetty-yhteyskatkos-data
  (let [graylogista-parsittu-data-sample (gen/sample (s/gen ::dgl/parsittu-yhteyskatkos-data))
        mahdollisesti-parsittavat-vektori-arvot #{:palvelut :ensimmaiset-katkokset :viimeiset-katkokset}
        mahdollisesti-parsittavat-ei-vektori-arvot #{:pvm :kello :kayttaja}
        mahdollisesti-parsittavat-arvot (set-math/union mahdollisesti-parsittavat-vektori-arvot
                                                        mahdollisesti-parsittavat-ei-vektori-arvot)]
    (testing "yhteyskatkosdatan järjestely ryhmäavaimena vektori"
      (doseq [parsittu-data graylogista-parsittu-data-sample]
        (let [jarjestys-avain (satunnainen-set-elementti mahdollisesti-parsittavat-arvot)
              ryhma-avain (satunnainen-set-elementti mahdollisesti-parsittavat-vektori-arvot)
              _ (println (pr-str jarjestys-avain " " ryhma-avain))
              jarjestetty-data (tiedot/jarjestele-yhteyskatkos-data jarjestys-avain ryhma-avain parsittu-data)]
          (is (s/valid? ::tiedot/jarjestetty-yhteyskatkos-data jarjestetty-data)
              (s/explain ::tiedot/jarjestetty-yhteyskatkos-data jarjestetty-data)))))))
    ; (testing "yhteyskatkosdatan järjestely ryhmäavaimena joku muu kuin vektori")
    ; (testing "yhteyskatkosdatan järjestely järjestysavaimena vektroi")
    ; (testing "yhteyskatkosdatan järjestely järjestysavaimena joku muu kuin vektori")))
    ;avs
