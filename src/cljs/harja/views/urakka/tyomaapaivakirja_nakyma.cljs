(ns harja.views.urakka.tyomaapaivakirja-nakyma
 "Työmaapäiväkirja näkymä"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(def toimituksen-tila [{:class "ok" :selitys "Ok"}
                       {:class "myohassa" :selitys "Myöhässä"}
                       {:class "puuttuu" :selitys "Puuttuu"}])

(defn tyomaapaivakirja-nakyma [e! {:keys [valittu-rivi] :as tiedot}]
  (let [toimitus-tiedot (get toimituksen-tila (:tila valittu-rivi))]
    [:span {:class "paivakirja-toimitus"}
     [:div {:class (str "pallura " (:class toimitus-tiedot))}]
     [:span {:class "kohta"} (:selitys toimitus-tiedot)]]

    [:<>
     [napit/takaisin "Takaisin" #(e! (tiedot/->PoistaRiviValinta)) {:luokka "nappi-reunaton"}]

     [:div {:style {:padding "48px 92px 72px"}}
      [:p (str valittu-rivi)]

      [:h3 {:class "header-yhteiset"} "UUD MHU 2022–2027"]
      [:h1 {:class "header-yhteiset"} "Työmaapäiväkirja 9.10.2022"]

      [:div {:class "nakyma-otsikko-tiedot"}

       [:span "Saapunut 11.10.2022 05:45"]
       [:span "Päivitetty 11.10.2022 05:45"]
       [:a {:href "url"} "Näytä muutoshistoria"]

       [:span {:class "paivakirja-toimitus"}
        [:div {:class (str "pallura " (:class toimitus-tiedot))}]
        [:span {:class "kohta"} (:selitys toimitus-tiedot)]]

       [:a
        [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "2 kommenttia"]]]

      [:hr]

      ;; Päivystäjät, Työnjohtajat
      [:div {:style {:padding-top "20px"}}

       [:h2 "Vahvuus"]
       [:div {:class "vahvuus-gridit"}
        [:div
         [:h3 "Päivystäjät"]

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
         ;; Gridille jokin atomi 
          [{:id 0 :alkupvm "00:00 - 00:00" :nimi "Test"}]]]


        [:div
         [:h3 "Työnjohtajat"]

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
         ;; Gridille jokin atomi 
          [{:id 0 :alkupvm "00:00 - 00:00" :nimi "Test"}]]]]]


      ;; Sääasemien tiedot
      [:div {:style {:padding-top "20px"}}

       [:h2 "Sääasemien tiedot"]
       [:div {:class "vahvuus-gridit"}
        [:div
         [:h3 "Tie 4, Oulu, Osoite"]

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
         ;; Gridille jokin atomi 
          [{:id 0 :alkupvm "00:00 - 00:00" :nimi "Test"}]]]


        [:div
         [:h3 "Tie 4, Oulu, Osoite"]

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
         ;; Gridille jokin atomi 
          [{:id 0 :alkupvm "00:00 - 00:00" :nimi "Test"}]]]]]]]))
