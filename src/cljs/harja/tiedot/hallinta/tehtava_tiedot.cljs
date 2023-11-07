(ns harja.tiedot.hallinta.tehtava-tiedot
  "Tehtävien ja tehtäväryhmien ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom nil))
(def nakymassa? (atom false))


(defrecord HaeTehtavaryhmaotsikot [])
(defrecord HaeTehtavaryhmaotsikotOnnistui [vastaus])
(defrecord HaeTehtavaryhmaotsikotEpaonnistui [vastaus])

(extend-protocol tuck/Event

  HaeTehtavaryhmaotsikot
  (process-event [{emailapi :emailapi} app]
    (tuck-apurit/post! :hae-mhu-tehtavaryhmaotsikot
      {}
      {:onnistui ->HaeTehtavaryhmaotsikotOnnistui
       :epaonnistui ->HaeTehtavaryhmaotsikotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :emailapi emailapi))

  HaeTehtavaryhmaotsikotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tehtavaryhmaotsikot vastaus))

  HaeTehtavaryhmaotsikotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Error: " (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil))))
