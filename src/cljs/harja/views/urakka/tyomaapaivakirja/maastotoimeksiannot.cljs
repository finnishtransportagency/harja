(ns harja.views.urakka.tyomaapaivakirja.maastotoimeksiannot
  "Työmaapäiväkirja näkymän maastotoimeksiannot- gridi"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn maastotoimeksiannot-grid []
  [:div {:style {:padding-top "20px"}}
   [:h2 "Viranomaispäätöksiin liittyvät maastotoimeksiannot"]
   [:div {:class "flex-gridit"}
    [:div
     [grid/grid {:tyhja "Ei Tietoja."
                 :tunniste :id
                 :voi-kumota? false
                 :piilota-border? true
                 :piilota-toiminnot? true
                 :jarjesta :id}

      [{:otsikko "Nimi"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}]
      ;; TODO
      ;; Gridille jokin atomi 
      [{:id 0 :alkupvm "00:00" :nimi "Test"}]]]]])
