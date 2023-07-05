(ns harja.tiedot.urakka.tienumerot-kartalla
  (:require [reagent.core :refer [atom]]
            [harja.atom :refer-macros [reaction<!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros
    [reagent.ratom :refer [reaction]]))

(defonce karttataso-tienumerot (atom nil))
(defonce karttataso-nakyvissa? (atom true))

(def tienumerot
  (reaction<!
    [{:keys [xmin ymin xmax ymax] :as kartalla-nakyva-alue} @nav/kartalla-nakyva-alue]
    {:odota 1000}
    (when (and xmin ymin xmax ymax)
      (let [tiemax (condp <= @nav/kartan-nakyvan-alueen-koko
                     750000 0
                     100000 39
                     50000 99
                     9999)]
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
                         39 2
                         99 1.7
                         999 1.5
                         9999 1.2)}})
        @tienumerot))))
