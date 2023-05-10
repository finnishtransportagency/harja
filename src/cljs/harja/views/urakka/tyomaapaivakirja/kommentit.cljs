(ns harja.views.urakka.tyomaapaivakirja.kommentit
  "Työmaapäiväkirja näkymän kommentit"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn kommentit []
  [:div.row.filtterit {:style {:padding "20px 92px 72px"}}
   [:h2 "Kommentit"]

   [:div {:class "kommentin-tiedot"}
    [:span "10.10.2022 15:45"]
    [:span "Timo Tilaaja"]]

   [:div {:class "kommentti"}
    [:h7 {:class "tieto-rivi"} "Tästähän puuttuu nelostien rekka-kolari"]]

   [:div {:class "kommentti-lisaa"}
    [:a
     [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "Lisää kommentti"]]]])
