(ns harja.tiedot.hallinta.tehtava-tiedot
  "Tehtävien ja tehtäväryhmien ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [cljs.core.async :refer [<! >! chan close!]]
            [cljs-http.client :as http]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def tila (atom nil))
(def nakymassa? (atom false))


(defrecord HaeTehtavaryhmat [])
(defrecord HaeTehtavaryhmatOnnistui [vastaus])
(defrecord HaeTehtavaryhmatEpaonnistui [vastaus])

(extend-protocol tuck/Event

  HaeTehtavaryhmat
  (process-event [{emailapi :emailapi} app]
    (tuck-apurit/post! :hae-mhu-tehtavaryhmat
      {}
      {:onnistui ->HaeTehtavaryhmatOnnistui
       :epaonnistui ->HaeTehtavaryhmatEpaonnistui
       :paasta-virhe-lapi? true})
    (js/console.log "HaeTehtavaryhmat")
    (assoc app :emailapi emailapi))

  HaeTehtavaryhmatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tehtavaryhmat vastaus))

  HaeTehtavaryhmatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Error: " (pr-str vastaus))
      (assoc app :tehtavaryhmat nil))))
