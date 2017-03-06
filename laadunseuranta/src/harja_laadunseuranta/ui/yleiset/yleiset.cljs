(ns harja-laadunseuranta.ui.yleiset.yleiset
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]))

(defn vihje [teksti]
  [:div.yleinen-pikkuvihje
   [kuvat/svg-sprite "ympyra-info-24"]
   teksti])