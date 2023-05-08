(ns harja.tiedot.tyomaapaivakirja
  "Työmaapäiväkirja kutsut"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]))

(defonce tila (atom {}))
(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])

(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (tuck-apurit/post! app :tyomaapaivakirja-hae
      {}
      {:onnistui ->HaeTiedotOnnistui
       :epaonnistui ->HaeTiedotEpaonnistui})) 

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (println "\n Vastaus: " vastaus)
    (assoc app :tiedot vastaus))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeTiedotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeTiedotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app))
