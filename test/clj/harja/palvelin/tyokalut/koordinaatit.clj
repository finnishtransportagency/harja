(ns harja.palvelin.tyokalut.koordinaatit
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.tyokalut.koordinaatit :as koordinaatit]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :http-palvelin (testi-http-palvelin)
                        :koordinaatit (component/using
                                        (koordinaatit/->Koordinaatit)
                                        [:http-palvelin])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))



(use-fixtures :each jarjestelma-fixture)

(deftest pisteen-hakeminen-kartalle-wgs84-koordinaateilla
  (let [x 25.472488
        y 65.011322
        oikea-vastaus {:type :point :coordinates [428003.60827314324 7210586.34520906]}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-piste-kartalle
                                +kayttaja-jvh+
                                {:x x
                                 :y y})]
    (is (= vastaus oikea-vastaus) "wgs84 koordinaattien muuttaminen pointiksi euref koordinaateilla ei toimi")))