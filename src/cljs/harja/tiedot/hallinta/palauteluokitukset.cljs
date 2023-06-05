(ns harja.tiedot.hallinta.palauteluokitukset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [taoensso.timbre :as log]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]))

(defonce tila (atom {}))

(defrecord HaePalauteluokitukset [])
(defrecord HaePalauteluokituksetOnnistui [vastaus])
(defrecord HaePalauteluokituksetEpaonnistui [vastaus])
(defrecord PaivitaPalauteluokitukset [])
(defrecord PaivitaPalauteluokituksetOnnistui [vastaus])
(defrecord PaivitaPalauteluokituksetEpaonnistui [vastaus])

(extend-protocol tuck/Event

  HaePalauteluokitukset
  (process-event [_ app]
    (-> app
      (assoc :palauteluokkahaku-kesken? true)
      (tuck-apurit/post! :hae-palauteluokitukset
        {}
        {:onnistui ->HaePalauteluokituksetOnnistui
         :epaonnistui ->HaePalauteluokituksetEpaonnistui})))

  HaePalauteluokituksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :palauteluokitukset vastaus
      :palauteluokkahaku-kesken? false))

  HaePalauteluokituksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Palauteluokitusten haku epäonnistui. Virhe: " vastaus)
    (viesti/nayta-toast! "Palauteluokitusten haku epäonnistui." :varoitus)
    (assoc app :palauteluokkahaku-kesken? false))

  PaivitaPalauteluokitukset
  (process-event [_ app]
    (-> app
      (assoc :palauteluokkapaivitys-kesken? true)
      (tuck-apurit/post! :paivita-palauteluokitukset
        {}
        {:onnistui ->PaivitaPalauteluokituksetOnnistui
         :epaonnistui ->PaivitaPalauteluokituksetEpaonnistui})))

  PaivitaPalauteluokituksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :palauteluokitukset vastaus
      :palauteluokkapaivitys-kesken? false))

  PaivitaPalauteluokituksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Palauteluokitusten haku epäonnistui. Virhe: " vastaus)
    (viesti/nayta-toast! "Palauteluokitusten haku epäonnistui." :varoitus)
    (assoc app :palauteluokkapaivitys-kesken? false)))
