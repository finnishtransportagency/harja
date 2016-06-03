(ns harja-laadunseuranta.kitkamittaus-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [dommy.core :as dommy]
            [cljs-react-test.simulate :as sim]
            [harja-laadunseuranta.testutils :refer [sel sel1]]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.kitkamittaus :as kitkamittaus])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component prepare-component-tests]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(prepare-component-tests)

(defn- syottokentan-teksti [komponentti]
  (dommy/text (dommy/sel1 komponentti [:div.syottokentta])))

(defn- paina-painiketta [komponentti painike]
  (sim/click (dommy/sel1 komponentti [painike]) nil)
  (reagent/flush))

(deftest kitkamittauskomponentti-test
  (let [model (atom {:min-length 2
                     :max-length 4
                     :desimaalit "0,"})
        tulos (atom nil)
        valmis-fn (fn [kitkamittaus] (reset! tulos kitkamittaus))]
    (with-component [kitkamittaus/kitkamittaus model valmis-fn]
      (let [komponentti (sel1 [:div.kitkamittaus])]
        (testing "Syottokentan teksti on alussa 0,"
          (is (= "0," (syottokentan-teksti komponentti))))
        (testing "Ensimmäinen desimaali"
          (paina-painiketta komponentti :span#btn2)
          (is (= "0,2" (syottokentan-teksti komponentti))))
        (testing "Toinen desimaali"
          (paina-painiketta komponentti :span#btn3)
          (is (= "0,23" (syottokentan-teksti komponentti))))
        (testing "Kolmas desimaali ei kirjaannu"
          (paina-painiketta komponentti :span#btn5)
          (is (= "0,23" (syottokentan-teksti komponentti))))
        (testing "Pyyhkiminen"
          (paina-painiketta komponentti :span#del)
          (is (= "0," (syottokentan-teksti komponentti))))
        (testing "Uusi desimaal"
          (paina-painiketta komponentti :span#btn5)
          (paina-painiketta komponentti :span#btn6)
          (is (= "0,56" (syottokentan-teksti komponentti))))
        (testing "OK-painike"
          (paina-painiketta komponentti :span#ok)         
          (is (= 0.56 @tulos))
          (is (= "0," (syottokentan-teksti komponentti))))))))
 
