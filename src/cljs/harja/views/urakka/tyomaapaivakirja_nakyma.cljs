(ns harja.views.urakka.tyomaapaivakirja-nakyma
 "Työmaapäiväkirja näkymä"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]))

(defn tyomaapaivakirja-nakyma [e! {:keys [valittu-rivi]}]
  [:<>
   [napit/takaisin "Takaisin" #(e! (tiedot/->PoistaRiviValinta)) {:luokka "nappi-reunaton"}]

   [:h3 {:class "header-yhteiset"} "UUD MHU 2022–2027"]
   [:h1 {:class "header-yhteiset"} "Työmaapäiväkirja 9.10.2022"]

   [:p (str valittu-rivi)]
   [:div.row.filtterit {:style {:padding "16px"}}]])
