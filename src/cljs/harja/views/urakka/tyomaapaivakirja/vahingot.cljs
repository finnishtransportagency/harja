(ns harja.views.urakka.tyomaapaivakirja.vahingot
  "Työmaapäiväkirja näkymän vahingot"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn vahingot-ja-onnettomuudet []
  [:div
   [:h2 "Vahingot ja onnettomuudet"]
   [:hr]
   [:h7 {:class "tieto-rivi"} "Vahinko: Rekka törmännyt keskikaiteeseen Vt 4 4/400/100-200, Kaide vaurioitunut 100 metriä"]
   [:hr]])
