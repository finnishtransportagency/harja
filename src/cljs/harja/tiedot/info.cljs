(ns harja.tiedot.info
  "Infosivun rajapinta kutsut ja eventit"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def data (atom {}))
(defrecord HaeKoulutusvideot [])
(defrecord HaeKoulutusvideotOnnistui [vastaus])
(defrecord HaeKoulutusvideotEpaonnistui [vastaus])

(extend-protocol tuck/Event

  HaeKoulutusvideot
  (process-event [_ _]
    (tuck-apurit/get! :hae-koulutusvideot
                      {:onnistui ->HaeKoulutusvideotOnnistui
                       :epaonnistui ->HaeKoulutusvideotEpaonnistui}))

  HaeKoulutusvideotOnnistui
  (process-event [{vastaus :vastaus} app]
                 (-> app
                     (assoc :videot vastaus)))

  HaeKoulutusvideotEpaonnistui
  (process-event [{vastaus :vastaus} _]
                 (js/console.log "HaeKoulutusvideotEpaonnistui :: vastaus" (pr-str vastaus))
                 (viesti/nayta-toast! (str "HaeKoulutusvideotEpaonnistui :: vastaus" (pr-str vastaus) ) :varoitus)))