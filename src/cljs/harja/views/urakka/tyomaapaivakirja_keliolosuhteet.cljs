(ns harja.views.urakka.tyomaapaivakirja-keliolosuhteet
  "Työmaapäiväkirja näkymän poikkeukselliset keliolosuhteet"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn poikkeukselliset-keliolosuhteet-grid []

  [:div {:style {:padding-top "10px"}}
   [:h2 "Poikkeukselliset paikalliset keliolosuhteet"]
   [:div {:class "flex-gridit"}
    [:div
     [:h3 {:class "gridin-otsikko"} "Omat havainnot"]

     [grid/grid {:tyhja "Ei Tietoja."
                 :tunniste :id
                 :voi-kumota? false
                 :piilota-border? true
                 :piilota-toiminnot? true
                 :jarjesta :id}

      [{:otsikko "Klo"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :alkupvm
        :leveys 0.1}

       {:otsikko "Paikka"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 0.33}
       
       {:otsikko "Havainto"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}]
      ;; TODO
      ;; Gridille jokin atomi 
      [{:id 0 :alkupvm "00:00 - 00:00" :nimi "Test"}]]]]])
