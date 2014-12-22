(ns harja.ui.yleiset
  "Yleisiä UI komponentteja"
  (:require [reagent.core :refer [atom]]))

(defn ajax-loader
  "Näyttää latausanimaatiokuvan ja optionaalisen viestin."
  ([] (ajax-loader nil))
  ([viesti]
     [:div.ajax-loader
      [:img {:src "/images/ajax-loader.gif"}]
      (when viesti
        [:div.viesti viesti])]))

  
