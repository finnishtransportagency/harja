(ns harja-laadunseuranta.ui.nappaimisto-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [dommy.core :as dommy]
            [harja-laadunseuranta.testutils :refer [sel sel1]]
            [harja-laadunseuranta.ui.nappaimisto :as nappaimisto]
            [harja-laadunseuranta.ui.ylapalkki :as ylapalkki]
            [cljs-react-test.utils])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component prepare-component-tests]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(prepare-component-tests)

(deftest nappaimisto-test
  (let [havainto {:nimi "Tasaus\u00ADpuute"
                  :avain :tasauspuute
                  :tyyppi :vali
                  :ikoni "epatasa-36"
                  :ikoni-lahde "livicons"
                  :mittaus {:nimi "Tasauspuute"
                            :tyyppi :talvihoito-tasaisuus
                            :yksikko "cm"}
                  :vaatii-nappaimiston? true}]
    (with-component [nappaimisto/nappaimistokomponentti
                     {:mittaussyotto-atom (atom {:nykyinen-syotto ""
                                                 :syotot []})
                      :soratiemittaussyotto-atom (atom {:tasaisuus 5
                                                        :kiinteys 5
                                                        :polyavyys 5})
                      :mittaustyyppi (get-in havainto [:mittaus :tyyppi])
                      :mittausyksikko (get-in havainto [:mittaus :yksikko])
                      :nimi (get-in havainto [:mittaus :nimi])
                      :avain (:avain havainto)}]
      (let [numeronappaimisto (sel1 [:div.numeronappaimisto])]
        (is numeronappaimisto "Numeronäppäimistö saatiin luotua")))))
