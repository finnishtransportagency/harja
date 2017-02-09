(ns harja-laadunseuranta.ui.nappaimisto-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [put! <! timeout chan close!]]
            [harja-laadunseuranta.testutils :refer [sel sel1 click]]
            [harja-laadunseuranta.ui.nappaimisto :as nappaimisto])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component prepare-component-tests]]
                   [cljs.core.async.macros :refer [go go-loop]]
                   [harja-laadunseuranta.macros :refer [after-delay]]))

(prepare-component-tests)

(deftest nappaimisto-test
  (let [syotto-atom (atom {:nykyinen-syotto ""
                           :syotot []})
        soratiemittaussyotto-atom (atom {:tasaisuus 5
                                         :kiinteys 5
                                         :polyavyys 5})
        havainto {:nimi "Tasaus\u00ADpuute"
                  :avain :tasauspuute
                  :tyyppi :vali
                  :ikoni "epatasa-36"
                  :ikoni-lahde "livicons"
                  :mittaus {:nimi "Tasauspuute"
                            :tyyppi :talvihoito-tasaisuus
                            :yksikko "cm"}
                  :vaatii-nappaimiston? true}]
    (with-component [nappaimisto/nappaimistokomponentti
                     {:mittaussyotto-atom syotto-atom
                      :soratiemittaussyotto-atom soratiemittaussyotto-atom
                      :mittaustyyppi (get-in havainto [:mittaus :tyyppi])
                      :mittausyksikko (get-in havainto [:mittaus :yksikko])
                      :nimi (get-in havainto [:mittaus :nimi])
                      :avain (:avain havainto)}]

      (async test-ok
        (go
          (let [numeronappaimisto (sel1 [:.numeronappaimisto])
                numeropainikkeet (reduce (fn [tulos numero]
                                           (assoc tulos (keyword (str numero))
                                                        (sel1 [keyword (str "#nappaimiston-painike-" numero)])))
                                         {}
                                         (range 0 10))]

            (is numeronappaimisto "Numeronäppäimistö saatiin luotua")
            (doseq [numerokomponentti (vals numeropainikkeet)]
              (is numerokomponentti "Kaikki numerokomponentit löytyy"))


            (click (:1 numeropainikkeet))
            (is (= (:nykyinen-syotto @syotto-atom) "1") "Numeron painaminen lisäsi syötön oikein")
            (test-ok)))))))
