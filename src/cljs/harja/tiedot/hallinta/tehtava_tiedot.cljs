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
(defrecord MuokkaaTehtavaryhmat [rivit])
(defrecord MuokkaaTehtavaryhmatOnnistui [vastaus])
(defrecord MuokkaaTehtavaryhmatEpaonnistui [vastaus])



(extend-protocol tuck/Event

  HaeTehtavaryhmaotsikot
  (process-event [_ app]
    (tuck-apurit/post! :hae-mhu-tehtavaryhmaotsikot
      {}
      {:onnistui ->HaeTehtavaryhmaotsikotOnnistui
       :epaonnistui ->HaeTehtavaryhmaotsikotEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeTehtavaryhmaotsikotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tehtavaryhmaotsikot vastaus))

  HaeTehtavaryhmaotsikotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaeTehtavaryhmaotsikotEpaonnistui :: error:" (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil)))

  MuokkaaTehtavaryhmat
  (process-event [{rivit :rivit} app]
    (tuck-apurit/post! :hallinta-tallenna-tehtavaryhmat
      {:muokatut-tehtavaryhmat rivit}
      {:onnistui ->MuokkaaTehtavaryhmatOnnistui
       :epaonnistui ->MuokkaaTehtavaryhmatEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  MuokkaaTehtavaryhmatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tehtavaryhmaotsikot vastaus))

  MuokkaaTehtavaryhmatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.error "MuokkaaTehtavaryhmatEpaonnistui :: error: " (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil))))
