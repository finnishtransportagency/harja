(ns harja.views.urakka.tyomaapaivakirja.muut-huomiot
  "Työmaapäiväkirja näkymän muut huomiot"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn muut-huomiot []
  [:div {:style {:padding-top "20px"}}
   [:h2 "Muut huomiot"]
   [:hr]

   [:hr]])
