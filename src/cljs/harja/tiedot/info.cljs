(ns harja.tiedot.info
  "Infosivun kutsut"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def data (atom {}))
(defrecord TallennaVideo [videot])
(defrecord HaeKoulutusvideot [])
(defrecord HaeKoulutusvideotOnnistui [vastaus])
(defrecord HaeKoulutusvideotEpaonnistui [vastaus])
(defrecord PaivitaKoulutusvideotOnnistui [vastaus])
(defrecord PaivitaKoulutusvideotEpaonnistui [vastaus])

(extend-protocol tuck/Event

  TallennaVideo
  (process-event [{videot :videot} app]
    (-> app
        (tuck-apurit/post! :paivita-koulutusvideot
                           {:tiedot videot}
                           {:onnistui ->PaivitaKoulutusvideotOnnistui
                            :epaonnistui ->PaivitaKoulutusvideotEpaonnistui})))

  HaeKoulutusvideot
  (process-event [_ app]
    (tuck-apurit/post! :hae-koulutusvideot
                       {}
                       {:onnistui ->HaeKoulutusvideotOnnistui
                        :epaonnistui ->HaeKoulutusvideotEpaonnistui})
    app)

  HaeKoulutusvideotOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc :videot vastaus)))

  HaeKoulutusvideotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeKoulutusvideotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeKoulutusvideotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  PaivitaKoulutusvideotOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc :videot vastaus)))

  PaivitaKoulutusvideotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "PaivitaKoulutusvideotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "PaivitaKoulutusvideotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app))