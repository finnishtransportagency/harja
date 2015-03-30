(ns harja.views.kartta.tasot
  "Määrittelee kartan näkyvät tasot. Tämä kerää kaikkien yksittäisten tasojen päällä/pois flägit ja osaa asettaa ne."
  (:require [reagent.core :refer [atom]]
            [harja.views.kartta.pohjavesialueet :refer [taso-pohjavesialueet pohjavesialueet]])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def geometriat (reaction
                 (concat @pohjavesialueet)))

(defn- taso-atom [nimi]
  (case nimi
    :pohjavesialueet taso-pohjavesialueet))
    
(defn taso-paalle! [nimi]
  (reset! (taso-atom nimi) true))

(defn taso-pois! [nimi]
  (reset! (taso-atom nimi) false))

(defn taso-paalla? [nimi]
  @(taso-atom nimi))


 
