(ns harja.geo-test
  (:require [clojure.test :refer :all]
            [harja.geo :as geo]))

(deftest extent
  (let [validi? (fn [[xmin ymin xmax ymax :as arr]]
                       (and (= 4 (count arr))
                            (= 4 (count (filter number? arr)))
                            (> xmax xmin)
                            (> ymax ymin)))
        geot [{:type   :line
               :points [[20 20] [10 10] [30 30]]}
              {:type  :multiline
               :lines [{:points [[20 20] [10 10] [30 30]]}
                       {:points [[100 100] [200 200]]}]}
              {:type        :polygon
               :coordinates [[20 20] [10 10] [30 30]]}
              {:type     :multipolygon
               :polygons [{:coordinates [[20 20] [10 10] [30 30]]}
                          {:coordinates [[100 100] [200 200]]}]}
              {:type :point
               :coordinates [20 20]}
              {:type :multipoint,
               :coordinates [{:type :point, :coordinates [204441.1091 6783735.807300001]}
                             {:type :point, :coordinates [104441.1091 6783735.80730002]}]}
              {:type :icon
               :coordinates [20 20]}
              {:type :circle
               :coordinates [20 20]}
              {:type   :viiva
               :points [[20 20] [10 10] [30 30]]}
              {:type  :moniviiva
               :lines [{:points [[20 20] [10 10] [30 30]]}
                       {:points [[100 100] [200 200]]}]}
              {:type :merkki
               :coordinates [20 20]}]]
    (is (every? validi? (map geo/extent geot)))))

(defn absolute [x] (Math/abs x))

(deftest extent-laajentaminen-prosentilla
  (testing "Apufunktiot"
    (let [extent [2 2 12 12]]
      (is (= 0.0 (#'geo/kasvata-vasemmalle extent 0.2)))
      (is (= 0.0 (#'geo/kasvata-alaspain extent 0.2)))
      (is (= 14.0 (#'geo/kasvata-oikealle extent 0.2)))
      (is (= 14.0 (#'geo/kasvata-ylospain extent 0.2)))))

  (testing "Laajentaminen prosentilla"
    (let [extent [2 2 12 12]]
      (is (= [0.0 0.0 14.0 14.0] (geo/laajenna-extent-prosentilla extent [0.2 0.2 0.2 0.2])))
      (is (= [2 2 14.0 12] (geo/laajenna-extent-prosentilla extent [0 0 0.2 0])))))

  (testing "Oletuksena muut suunnat kasvaa saman verran, paitsi ylöspäin aina enemmän."
    (let [extent [2 2 12 12]
          [minx miny maxx maxy :as muutokset] (map - (geo/laajenna-extent-prosentilla extent) extent)]
      (is (= (absolute (int minx)) (absolute (int maxx))) "Oletuksena extentin pitäis laajeta vasemmalle ja oikealle saman verran")
      (is (< (absolute miny) (absolute maxy)) "Oletuksena extentin pitäis kasvaa ylöspäin enemmän kuin alaspäin"))))

(deftest pinta-ala
  (testing "Pinta-ala lasketaan oikein"
    (is (= 100 (geo/extent-pinta-ala [0 0 10 10])))
    (is (= 100 (geo/extent-pinta-ala [0 0 -10 -10])))
    (is (= 100 (geo/extent-pinta-ala [0 0 10 -10]))))

  (testing "Älä laajenna pinta-alaan, jos on jo isompi"
    (is (= [0 0 10 10] (geo/laajenna-pinta-alaan [0 0 10 10] 5)))
    (is (= [0 0 10 10] (geo/laajenna-pinta-alaan [0 0 10 10] 10))))

  (testing "Laajenna tasaisesti joka suuntaan"
    (is (= [-5.0 -5.0 15.0 15.0] (geo/laajenna-pinta-alaan [0 0 10 10] 400)))))

(deftest geometrian-ensimmainen-piste-loytyy
(let [lahtoarvo-viiva {:type :line, :points [[374842.77830479154 7162212.596467628] [374843.1451795721 7162214.560333616]]}
     lahtoarvo-piste {:type :point, :coordinates [[374842.77830479154 7162212.596467628]]}
     lahtoarvo-monta-viivaa {:type :multiline, :lines [{:type :line, :points [[357002.03819728625 7142511.219114429] [357009.824 7142524.667] [357010.555 7142525.945] [357021.461 7142545.041]]} {:type :line, :points [[357021.461 7142545.041] [357053.15097933455 7142599.537464753]]}]}
     lahtoarvo-monta-pistetta {:type :multipoint, :coordinates [{:type :point, :coordinates [357002.03819728625 7142511.219114429]} {:type :point, :coordinates [357053.15097933455 7142599.537464753]}]}]
  (is (= [374842.77830479154 7162212.596467628](geo/ensimmaisen-pisteen-koordinaatit lahtoarvo-viiva)) "Viivageometria (linestring) toimii, ensimmäinen piste")
  (is (= [374842.77830479154 7162212.596467628](geo/ensimmaisen-pisteen-koordinaatit lahtoarvo-piste)) "Pistegeometria (point) toimii, ensimmäinen piste")
  (is (= [357002.03819728625 7142511.219114429](geo/ensimmaisen-pisteen-koordinaatit lahtoarvo-monta-viivaa)) "Moniviivainen geometria (multilinestring) toimii, ensimmäinen piste")
  (is (= [357002.03819728625 7142511.219114429](geo/ensimmaisen-pisteen-koordinaatit lahtoarvo-monta-pistetta)) "Monipisteinen geometria (point) toimii, ensimmäinen piste")))

(deftest geometrian-viimeinen-piste-loytyy
  (let [lahtoarvo-viiva {:type :line, :points [[374842.77830479154 7162212.596467628] [374843.1451795721 7162214.560333616]]}
        lahtoarvo-piste {:type :point, :coordinates [[374842.77830479154 7162212.596467628]]}
        lahtoarvo-monta-viivaa {:type :multiline, :lines [{:type :line, :points [[357002.03819728625 7142511.219114429] [357009.824 7142524.667] [357010.555 7142525.945] [357021.461 7142545.041]]} {:type :line, :points [[357021.461 7142545.041] [357053.15097933455 7142599.537464753]]}]}
        lahtoarvo-monta-pistetta {:type :multipoint, :coordinates [{:type :point, :coordinates [357002.03819728625 7142511.219114429]} {:type :point, :coordinates [357053.15097933455 7142599.537464753]}]}]
    (is (= [374843.1451795721 7162214.560333616](geo/viimeisen-pisteen-koordinaatit lahtoarvo-viiva)) "Viivageometria (linestring) toimii, viimeinen piste")
    (is (= [374842.77830479154 7162212.596467628](geo/viimeisen-pisteen-koordinaatit lahtoarvo-piste)) "Pistegeometria (point) toimii, viimeinen piste")
    (is (= [357053.15097933455 7142599.537464753](geo/viimeisen-pisteen-koordinaatit lahtoarvo-monta-viivaa)) "Moniviivainen geometria (multilinestring) toimii, viimeinen piste")
    (is (= [357053.15097933455 7142599.537464753](geo/viimeisen-pisteen-koordinaatit lahtoarvo-monta-pistetta)) "Monipisteinen geometria (point) toimii, viimeinen piste")))
