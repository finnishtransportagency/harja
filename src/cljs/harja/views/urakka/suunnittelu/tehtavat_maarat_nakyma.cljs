(ns harja.views.urakka.suunnittelu.tehtavat-maarat-nakyma
  (:require [tuck.core :as tuck]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka :as u]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot :as tiedot]
            [harja.views.urakka.valinnat :as urakka-valinnat]))

(defn tehtava-taulukko [e! {:keys [haku-kaynnissa? tehtavat-ja-maarat nayta-muuttuneet-tehtavat]}]
  (let [sarakkeet [{:otsikko "Tehtävä" :leveys "30%" :nimi :nimi}
                   {:otsikko "Sopimuksen määrä" :leveys "15%" :nimi :sopimus-maara}
                   {:otsikko "Muutokset" :leveys "15%" :nimi :muutokset}
                   {:otsikko "Muuttunut määrä" :leveys "20%" :nimi :muuttunut-maara}]]
    (if haku-kaynnissa?
      [ajax-loader-pieni]
      [grid/grid
       {:otsikko ""}
       sarakkeet
       tehtavat-ja-maarat])))

(defn nakyma [e! {:keys [haku-kaynnissa? nayta-muuttuneet-tehtavat] :as app}]
  (let [valittu-toimenpide {:nimi "Talvihoito"}]
    [:div#vayla

     [:div.row
      [:div.col-xs-12
       [:h1 "Tehtävät ja määrät"]]]

     [:div.flex-row {:style {:justify-content "flex-start"}}
      [:div.filtteri {:style {:width "200px"}}
       [urakka-valinnat/paivittava-urakkavuosi-tuck
        @u/valittu-aikavali
        #(e! (tiedot/->HaeTehtavatJaMaarat nil)) haku-kaynnissa? false]]

      [:div.filtteri.label-ja-kentta {:style {:padding-left "1rem" :padding-top "23px"}}
       [:div
        [napit/yleinen-toissijainen "Tuo tiedot excelistä" #(js/console.log "Tuo tiedot excelistä :: Ei vielä toiminnallisuutta")
         {:vayla-tyyli? true}]]]]

     [:div.flex-row {:style {:justify-content "flex-start"}}
      [:div.filtteri {:style {:width "200px"}}
       [yleiset/pudotusvalikko
        "Toimenpide"
        {:valinta valittu-toimenpide
         :valitse-fn #(e! (tiedot/->HaeTehtavatJaMaarat {:toimenpide %}))
         :vayla-tyyli? true
         :format-fn :nimi}
        [{:nimi "Näytä kaikki"} {:nimi "Talvihoito"} {:nimi "Liikenneympäristön hoito"}]]]

      [:div.filtteri.label-ja-kentta {:style {:padding-left "1rem" :padding-top "45px"}}
       [:div
        [kentat/raksiboksi {:teksti "Näytä vain muuttuneet tehtävät"
                            :tiivis? true
                            :toiminto #(e! (tiedot/->ToggleMuuttuneetTehtavat))}
         nayta-muuttuneet-tehtavat]]]]

     [:div.row
      [:div.col-xs-12.col-md-2
       [napit/yleinen-ensisijainen "Lisää muutos" #(js/console.log "Lisää muutos :: Ei vielä toiminnallisuutta")]]]

     (tehtava-taulukko e! app)
     [debug/debug app]]))

(defn tehtavat-maarat*
  [e! _]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTehtavatJaMaarat nil)))
    (fn [e! app]
      [:div
       [nakyma e! app]])))

(defn tehtavat-maarat []
  (tuck/tuck tila/suunnittelu-tehtavat-maarat tehtavat-maarat*))
