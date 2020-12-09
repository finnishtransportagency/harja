(ns harja.palvelin.tyokalut.koordinaatit-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.tyokalut.koordinaatit :as koordinaatit]
            [com.stuartsierra.component :as component]))

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
        oikea-vastaus {:type :point :coordinates [428003.608273143 7210586.34520906]}
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-piste-kartalle
                                +kayttaja-jvh+
                                {:x x
                                 :y y})
        pyorista #(/ (Math/round (* %1 (Math/pow 10 %2))) (Math/pow 10 %2))
        vastaus (-> vastaus
                    (update-in [:coordinates 0] #(pyorista % 9))
                    (update-in [:coordinates 1] #(pyorista % 8)))]
    (is (= vastaus oikea-vastaus) "wgs84 koordinaattien muuttaminen pointiksi euref koordinaateilla ei toimi")))
