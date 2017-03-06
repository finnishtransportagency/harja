(ns harja-laadunseuranta.tiedot.paikannus-test
  (:require [cljs.test :as t :refer-macros [deftest is testing run-tests async]]
            [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [<! >! chan close!]]
            [harja-laadunseuranta.tiedot.paikannus :as p])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.macros :refer [after-delay]]
                   [reagent.ratom :refer [reaction]]))

(deftest testaa-paikannus
  (async paikka-saatu
    (let [paikka-atomi (atom nil)
          paikannus-id (p/kaynnista-paikannus {:sijainti-atom paikka-atomi})]
      (add-watch paikka-atomi :muuttui (fn [_ _ _ val]
                                         #_(is (nil? val))
                                         (p/lopeta-paikannus paikannus-id)
                                         (paikka-saatu))))))

(deftest etaisyys-test
  (is (= (p/etaisyys {:lat 1 :lon 1}
                     {:lat 2 :lon 2})
         (Math/sqrt 2))))

(deftest latlon-konversio-test
  (is (= (p/konvertoi-latlon #js {:coords #js {:latitude 65
                                               :longitude 25
                                               :heading 45
                                               :accuracy 10
                                               :speed 30}})
         {:lat 7209946.446847636
          :lon 405698.9876087785 
          :heading 45
          :accuracy 10
          :speed 30})))

(def fake-location {:lat 65
                    :lon 25
                    :heading 0
                    :accuracy 10
                    :speed 0})

(def tyhja-location {:edellinen nil
                     :nykyinen nil})

(deftest sijainnin-paivitys-test
  (testing "Paikannuksen päivitys pitäisi pitää edellisen paikannuksen tallessa aikaleiman kera"
    (let [paivitys1 (p/paivita-sijainti tyhja-location fake-location 10)
          paivitys2 (p/paivita-sijainti paivitys1 fake-location 20)]
      (is (nil? (:edellinen paivitys1)))
      (is (:nykyinen paivitys1))
      (is (= 10 (:timestamp (:nykyinen paivitys1))))
    
      (is (= 10 (:timestamp (:edellinen paivitys2))))
      (is (= (:edellinen paivitys2) (:nykyinen paivitys1)))
      (is (:nykyinen paivitys2))
      (is (= 20 (:timestamp (:nykyinen paivitys2)))))))
