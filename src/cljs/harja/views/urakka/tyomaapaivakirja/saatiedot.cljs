(ns harja.views.urakka.tyomaapaivakirja.saatiedot
  "Työmaapäiväkirja näkymän Sääasemien tieto- gridit"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn saatiedot-grid []
  [:div {:style {:padding-top "10px"}}
   [:h2 "Sääasemien tiedot"]
   [:div {:class "flex-gridit"}
    [:div
     [:h3 {:class "gridin-otsikko"} "Tie 4, Oulu, Osoite"]

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
        :leveys 1}

       {:otsikko "Ilma"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "Ilma"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "Tie"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "S-olom"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "K-tuuli"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "K-Sum"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}]
      ;; TODO 
      ;; Gridille jokin atomi 
      [{:id 0 :alkupvm "00:00" :nimi "Test"}]]]

    [:div
     [:h3 {:class "gridin-otsikko"} "Tie 4, Oulu, Osoite"]

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
        :leveys 1}

       {:otsikko "Ilma"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "Ilma"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "Tie"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "S-olom"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "K-tuuli"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}

       {:otsikko "K-Sum"
        :otsikkorivi-luokka "nakyma-otsikko"
        :tyyppi :string
        :nimi :nimi
        :solun-luokka (fn [_ _]
                        "nakyma-valkoinen-solu")
        :leveys 1}]
      ;; TODO 
      ;; Gridille jokin atomi 
      [{:id 0 :alkupvm "00:00" :nimi "Test"}]]]]])
