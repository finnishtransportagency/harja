(ns harja.views.urakka.tyomaapaivakirja.liikenteenohjaukset
  "Työmaapäiväkirja näkymän tilapäiset liikenteenohjaukset"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn tilapaiset-liikenteenohjaukset []
  [:div {:style {:padding-top "20px"}}
   [:h2 "Tilapäiset liikenteenohjaukset"]
   [:hr]
   [:h7 {:class "tieto-rivi"} "Liikenne poikki onnettomuuden takia klo 08:45 - 9:22: Vt 4 4/400/100-200. Kiertotie 847/4/0-2000. Käytetty liikenteenohjausvaunua ohjaukseen."]
   [:hr]])
