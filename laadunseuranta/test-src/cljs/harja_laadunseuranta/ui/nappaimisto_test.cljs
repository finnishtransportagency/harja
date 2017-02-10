(ns harja-laadunseuranta.ui.nappaimisto-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [put! <! timeout chan close!]]
            [harja.testutils.shared-testutils :refer [sel sel1]]
            [harja-laadunseuranta.ui.nappaimisto :as nappaimisto]
            [cljs-react-test.simulate :as sim] ;; click-macro vaatii!
            [clojure.string :as str])
  (:require-macros [harja-laadunseuranta.test-macros :refer [with-component
                                                             prepare-component-tests]]
                   [harja.testutils.macros :refer [klikkaa-ja-odota]]
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
                pilkkupainike (sel1 [:#nappaimiston-painike-pilkku])
                numeropainikkeet (reduce (fn [tulos numero]
                                           (assoc tulos (keyword (str numero))
                                                        (sel1 [keyword (str "#nappaimiston-painike-" numero)])))
                                         {}
                                         (range 0 10))]

            ;; Komponentit mountattu ok
            (is numeronappaimisto "Numeronäppäimistö saatiin luotua")
            (is pilkkupainike "Pilkkupainike saatiin luotua")
            (doseq [numerokomponentti (vals numeropainikkeet)]
              (is numerokomponentti "Kaikki numerokomponentit löytyy"))

            ;; Testisyöttöjä
            (is (str/includes? (.-className pilkkupainike) "nappaimiston-painike-disabloitu") "Pilkkua ei voi laittaa ensimmäiseksi merkiksi")
            (klikkaa-ja-odota (:1 numeropainikkeet))
            (is (= (:nykyinen-syotto @syotto-atom) "1") "Syöttö onnistui")
            (is (not (str/includes? (.-className pilkkupainike) "nappaimiston-painike-disabloitu")))

            (klikkaa-ja-odota (:2 numeropainikkeet))
            (is (= (:nykyinen-syotto @syotto-atom) "12") "Syöttö onnistui")

            (klikkaa-ja-odota (:3 numeropainikkeet))
            (is (= (:nykyinen-syotto @syotto-atom) "123") "Syöttö onnistui")

            (klikkaa-ja-odota (:3 numeropainikkeet))
            (is (= (:nykyinen-syotto @syotto-atom) "123") "Ei anna syöttää yli rajojen")

            (klikkaa-ja-odota pilkkupainike)
            (is (= (:nykyinen-syotto @syotto-atom) "123,") "Pilkun syöttö onnistui")

            (klikkaa-ja-odota (:1 numeropainikkeet))
            (is (= (:nykyinen-syotto @syotto-atom) "123,1") "Syöttö onnistui")

            (klikkaa-ja-odota (:1 numeropainikkeet))
            (is (= (:nykyinen-syotto @syotto-atom) "123,1") "Ei anna syöttää yli rajojen")
            (test-ok)))))))
