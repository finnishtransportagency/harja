(ns harja.tiedot.urakka.tienumerot-kartalla
  (:require [reagent.core :refer [atom]]
            [harja.atom :refer-macros [reaction<!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.openlayers :as ol])
  (:require-macros
    [reagent.ratom :refer [reaction]]))

(defonce karttataso-tienumerot (atom nil))
(defonce karttataso-nakyvissa? (atom true))

(def valtatie-max 39) ;; Tiet 1-39 ovat valtateitä
(def kantatie-max 99) ;; Tiet 40-99 ovat kantateitä
(def seututie-max 999) ;; Tiet 100-999 ovat seututeitä
(def yhdystie-max 9999) ;; Ja loput ovat yhdysteitä.

(def tienumerot
  (reaction<!
    [{:keys [xmin ymin xmax ymax] :as kartalla-nakyva-alue} @nav/kartalla-nakyva-alue]
    {:odota 1000}
    (when (and xmin ymin xmax ymax)
      (let [tiemax (condp >= (ol/nykyinen-zoom-taso)
                     5 0 ;; Ei näytetä ollenkaan teitä
                     7 valtatie-max
                     9 kantatie-max
                     yhdystie-max)]
        (when (and @karttataso-nakyvissa? (not= 0 tiemax))
          (k/post! :hae-tienumerot-kartalle
            (merge kartalla-nakyva-alue
              {:tiemax tiemax})))))))

(def tienumerot-kartalla
  (reaction
    (when @tienumerot
      (mapv (fn [tienumero]
              {:alue
               {:type :teksti
                :coordinates (:coordinates (:geom tienumero))
                :text (:tie tienumero)
                :scale (condp >= (:tie tienumero)
                         ;; Näytetään pienemmät tiet pienemmällä tekstillä.
                         valtatie-max 2
                         kantatie-max 1.7
                         seututie-max 1.5
                         yhdystie-max 1.2)}})
        @tienumerot))))
