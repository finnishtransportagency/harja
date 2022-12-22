(ns harja.tiedot.info
  "Infosivun kutsut"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]))

(defonce tila (atom {}))
(defrecord TallennaVideo [videot])
(defrecord HaeKoulutusvideot [])
(defrecord HaeKoulutusvideotOnnistui [vastaus])
(defrecord HaeKoulutusvideotEpaonnistui [vastaus])
(defrecord PaivitaKoulutusvideotOnnistui [vastaus])
(defrecord PaivitaKoulutusvideotEpaonnistui [vastaus])

(extend-protocol tuck/Event

  TallennaVideo
  (process-event [{videot :videot} app]
    (tuck-apurit/post! app :paivita-koulutusvideot
                       {:tiedot videot}
                       {:onnistui ->PaivitaKoulutusvideotOnnistui
                        :epaonnistui ->PaivitaKoulutusvideotEpaonnistui}))

  HaeKoulutusvideot
  (process-event [_ app]
    (tuck-apurit/post! app :hae-koulutusvideot
                       {}
                       {:onnistui ->HaeKoulutusvideotOnnistui
                        :epaonnistui ->HaeKoulutusvideotEpaonnistui}))

  HaeKoulutusvideotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :videot vastaus))

  HaeKoulutusvideotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeKoulutusvideotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeKoulutusvideotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  PaivitaKoulutusvideotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :videot vastaus))

  PaivitaKoulutusvideotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PaivitaKoulutusvideotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "PaivitaKoulutusvideotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app))