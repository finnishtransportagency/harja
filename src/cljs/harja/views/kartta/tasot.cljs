(ns harja.views.kartta.tasot
  "Määrittelee kartan näkyvät tasot. Tämä kerää kaikkien yksittäisten tasojen päällä/pois flägit ja osaa asettaa ne."
  (:require [reagent.core :refer [atom]]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.tiedot.sillat :as sillat])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def geometriat (reaction
                 (loop [geometriat (transient [])
                        [g & gs] (concat @pohjavesialueet/pohjavesialueet @sillat/sillat)]
                   (if-not g
                     (persistent! geometriat)
                     (recur (conj! geometriat g) gs)))))


(defn- taso-atom [nimi]
  (case nimi
    :pohjavesialueet pohjavesialueet/taso-pohjavesialueet
    :sillat sillat/taso-sillat))
    
(defn taso-paalle! [nimi]
  (reset! (taso-atom nimi) true))

(defn taso-pois! [nimi]
  (reset! (taso-atom nimi) false))

(defn taso-paalla? [nimi]
  @(taso-atom nimi))


 
