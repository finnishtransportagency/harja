(ns harja.tiedot.urakka.tienumerot-kartalla
  (:require [reagent.core :refer [atom] :as r]
            [harja.atom :refer-macros [reaction<!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros
    [reagent.ratom :refer [reaction]]))

(defonce karttataso-tienumerot (atom nil))
(defonce karttataso-nakyvissa? (atom true))

(def tiemax-atom (atom 9999))

(def tienumerot
  (reaction<!
    [{:keys [xmin ymin xmax ymax] :as kartalla-nakyva-alue} @nav/kartalla-nakyva-alue]
    {:odota 1000}
    (when (and xmin ymin xmax ymax)
      (k/post! :hae-tienumerot-kartalle
        (merge kartalla-nakyva-alue
          {:tiemax @tiemax-atom})))))

(def tienumerot-kartalla
  (reaction
    (when @tienumerot
      (mapv (fn [tienumero]
              {:alue
               {:type :teksti
                :coordinates (:coordinates (:geom tienumero))
                :text (:tie tienumero)}})
        @tienumerot))))
