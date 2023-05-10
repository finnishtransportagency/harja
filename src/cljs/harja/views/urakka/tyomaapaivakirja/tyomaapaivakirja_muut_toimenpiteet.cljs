(ns harja.views.urakka.tyomaapaivakirja.tyomaapaivakirja-muut-toimenpiteet
  "Työmaapäiväkirja näkymän muut toimenpiteet"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn muut-toimenpiteet-grid []
  [:div {:style {:padding-top "10px"}}
   [:h2 "Muut toimenpiteet"]
   [:div {:class "flex-gridit"}
    [:div
     [grid/grid {:tyhja "Ei Tietoja."
                 :tunniste :id
                 :voi-kumota? false
                 :piilota-border? true
                 :piilota-toiminnot? true
                 :jarjesta :id}

      [{:otsikko "Aikaväli"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :alkupvm
        :leveys 0.023}

       {:otsikko "Toimenpide"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 0.3}]
      ;; TODO
      ;; Gridille jokin atomi 
      [{:id 0 :alkupvm "00:00" :nimi "Test"}]]]]])
