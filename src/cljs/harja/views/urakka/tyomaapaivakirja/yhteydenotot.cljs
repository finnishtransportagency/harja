(ns harja.views.urakka.tyomaapaivakirja.yhteydenotot
  "Työmaapäiväkirja näkymän yhteydenotot"
  (:require [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]))

(defn yhteydenotot []
  [:div {:style {:padding-top "10px"}}
   [:h2 "Yhteydenotot ja palautteet, jotka edellyttävät toimenpiteitä"]
   [:hr]

   [:h7 {:class "tieto-rivi"} "Väyläviraston siltainsinööri haluaisi käydä silloilla x ja y tekemässä erikoistarkastuksen"]
   [:hr]
   [:h7 {:class "tieto-rivi"} "Kaupungin kunnossapitopäällikkö otti yhteyttä viherhoidon rajoista"]

   [:hr]])
