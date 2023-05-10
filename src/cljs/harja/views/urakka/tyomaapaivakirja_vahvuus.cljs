(ns harja.views.urakka.tyomaapaivakirja-vahvuus
  "Työmaapäiväkirja näkymän Vahvuus- gridit"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn vahvuus-grid []
  [:div {:style {:padding-top "10px"}}
   [:h2 "Vahvuus"]
   [:div {:class "flex-gridit"}
    [:div
     [:h3 {:class "gridin-otsikko"} "Päivystäjät"]

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
        :leveys 0.25}

       {:otsikko "Nimi"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}]
      ;; TODO
      ;; Gridille jokin atomi 
      [{:id 0 :alkupvm "00:00 - 00:00" :nimi "Test"}]]]

    [:div
     [:h3 {:class "gridin-otsikko"} "Työnjohtajat"]
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
        :leveys 0.25}

       {:otsikko "Nimi"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}]
      ;; TODO
      ;; Gridille jokin atomi 
      [{:id 0 :alkupvm "00:00 - 00:00" :nimi "Test"}]]]]])
