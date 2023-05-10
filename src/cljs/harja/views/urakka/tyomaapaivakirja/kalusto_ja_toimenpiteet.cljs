(ns harja.views.urakka.tyomaapaivakirja.kalusto-ja-toimenpiteet
  "Työmaapäiväkirja näkymän kalusto ja tien toimenpiteet"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn kalusto-ja-tien-toimenpiteet-grid []
  [:div {:style {:padding-top "10px"}}
   [:h2 "Kalusto ja tielle tehdyt toimenpiteet"]
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
        :leveys 0.12}

       {:otsikko "Peruskalusto (KA/TR)"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 0.3}

       {:otsikko "Lisäkalusto"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 0.3}

       {:otsikko "Toimenpide"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}]
      ;; TODO
      ;; Gridille jokin atomi 
      [{:id 0 :alkupvm "00:00" :nimi "Test"}]]]]])
