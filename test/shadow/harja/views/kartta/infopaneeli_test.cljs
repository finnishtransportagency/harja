(ns harja.views.kartta.infopaneeli-test
  (:require [cljs.test :refer-macros [deftest is]]
            [cljs-time.core :as time]
            [harja.ui.kartta.infopaneelin-sisalto :as infopaneelin-sisalto]
            [harja.views.kartta.infopaneeli :as infopaneeli]
            [react-testing-library-cljs.reagent.fire-event :as fire-event]
            [react-testing-library-cljs.reagent.render :as render]
            [react-testing-library-cljs.screen :as screen]
            [reagent.core :as r]))

(defn tee-aika [vuosi kuukausi paiva tunti minuutti sekunti]
  (time/local-date-time vuosi kuukausi paiva tunti minuutti sekunti))

(def testidata
  {:koordinaatti [430704 7212576]
   :haetaan? false
   :asiat [{:id 104
             :alkanut (tee-aika 2016 12 1 12 0 0)
             :paattynyt (tee-aika 2016 12 1 12 0 0)
             :toimenpide "Auraus ja sohjonpoisto"
             :suorittaja {:nimi "ZYX Tekeminen Oy"}
             :tyyppi-kartalla :toteuma
             :tierekisteriosoite {:numero 20
                                  :alkuosa 5
                                  :alkuetaisyys 0
                                  :loppuosa 6
                                  :loppuetaisyys 100}
             :tehtavat [{:toimenpide "Auraus ja sohjonpoisto"
                         :maara 23
                         :yksikko "tiekm"
                         :id 104}]}
            {:id 105
             :alkanut (tee-aika 2016 12 2 11 0 0)
             :paattynyt (tee-aika 2016 12 2 11 0 0)
             :toimenpide "Auraus ja sohjonpoisto"
             :suorittaja {:nimi "ZYX Tekeminen Oy"}
             :tyyppi-kartalla :toteuma
             :tierekisteriosoite {:numero 20
                                  :alkuosa 1
                                  :alkuetaisyys 0
                                  :loppuosa 5
                                  :loppuetaisyys 100}
             :tehtavat [{:toimenpide "Auraus ja sohjonpoisto"
                         :maara 32
                         :yksikko "tiekm"
                         :id 105}]}
            {:id 106
             :alkanut (tee-aika 2016 12 2 11 0 0)
             :paattynyt (tee-aika 2016 12 2 11 0 0)
             :toimenpide "Liukkaudentorjunta suolaamalla (materiaali)"
             :suorittaja {:nimi "ZYX Tekeminen Oy"}
             :tyyppi-kartalla :toteuma
             :tierekisteriosoite {:numero 20
                                  :alkuosa 6
                                  :alkuetaisyys 0
                                  :loppuosa 7
                                  :loppuetaisyys 100}
             :tehtavat [{:toimenpide "Liukkaudentorjunta suolaamalla (materiaali)"
                         :maara 35
                         :yksikko "tiekm"
                         :id 106}]}]})

(deftest edellytykset
  (is (pos? (count (:asiat testidata))))
  (is (not (empty? (infopaneelin-sisalto/skeemamuodossa (:asiat testidata))))))

(deftest otsikot
  (let [suljettu? (r/atom false)
        piilota-fn! #(reset! suljettu? true)
        linkkifunktiot (r/atom {:toteuma {:teksti "linkkinappi"
                                          :toiminto (fn [_] nil)}})]
    (render/render! [:div.kartan-infopaneeli
                     [infopaneeli/infopaneeli testidata piilota-fn! linkkifunktiot]])
    (is (= 1 (count (screen/get-all-by-role "button"))))
    (is (= 2 (count (screen/get-all-by-text #"Auraus ja sohjonpoisto"))))
    (is (some? (screen/get-by-text #"Liukkaudentorjunta suolaamalla")))
    (is (nil? (screen/query-by-text "ZYX Tekeminen Oy")))
    (is (nil? (screen/query-by-text "23 tiekm")))

    (fire-event/click (first (screen/get-all-by-text #"Auraus ja sohjonpoisto")))
    (is (some? (screen/get-by-text "ZYX Tekeminen Oy")))
    (is (some? (screen/get-by-text "32,00 tiekm")))
    (is (some? (screen/get-by-role "button" {:name "linkkinappi"})))

    (fire-event/click (screen/get-by-role "button" {:name "sulje"}))
    (is (true? @suljettu?))))
