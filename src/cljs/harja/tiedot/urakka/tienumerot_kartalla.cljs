(ns harja.tiedot.urakka.tienumerot-kartalla
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer-macros [reaction<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature kartalla-esitettavaan-muotoon]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
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
      (k/post! :hae-tienumerot-kartalle
        kartalla-nakyva-alue))))

(def tienumerot-kartalla
  (reaction
    (when @tienumerot
      (mapv (fn [tienumero]
              (println tienumero)
              {:alue
               {:type :teksti
                :coordinates (:coordinates (:geom tienumero))
                :text (:tie tienumero)
                }
               #_(maarittele-feature (:geom tienumero)
                       false
                       (merge asioiden-ulkoasu/tr-ikoni
                         {:text "asdf"})
                       asioiden-ulkoasu/tr-viiva)})
        @tienumerot))))
