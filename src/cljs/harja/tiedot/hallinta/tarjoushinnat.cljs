(ns harja.tiedot.hallinta.tarjoushinnat
  (:require [cljs.core.async :refer [>! <!]]
            [harja.loki :as log]
            [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:vain-puutteelliset? false
                 :haettava-urakka ""}))

(defrecord HaeTarjoushinnat [])
(defrecord HaeTarjoushinnatOnnistui [vastaus])
(defrecord HaeTarjoushinnatEpaonnistui [vastaus])

(defrecord PaivitaTarjoushinnat [tiedot paluukanava])
(defrecord PaivitaTarjoushinnatOnnistui [vastaus paluukanava])
(defrecord PaivitaTarjoushinnatEpaonnistui [vastaus paluukanava])

(defrecord AsetaVainPuutteelliset [vain-puutteelliset?])
(defrecord AsetaHaettavaUrakka [teksti])

(extend-protocol tuck/Event
  HaeTarjoushinnat
  (process-event [_ app]
    (tuck-apurit/post! :hae-tarjoushinnat
      {}
      {:onnistui ->HaeTarjoushinnatOnnistui
       :epaonnistui ->HaeTarjoushinnatEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :haku-kaynnissa? true))

  HaeTarjoushinnatOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjoushinnat vastaus)))

  HaeTarjoushinnatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tarjoushintojen haku epäonnistui" :varoitus)
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjoushinnat nil)))

  PaivitaTarjoushinnat
  (process-event [{:keys [tiedot paluukanava]} app]
    (tuck-apurit/post! app :paivita-tarjoushinnat
      tiedot
      {:onnistui ->PaivitaTarjoushinnatOnnistui
       :epaonnistui ->PaivitaTarjoushinnatEpaonnistui
       :onnistui-parametrit [paluukanava]
       :epaonnistui-parametrit [paluukanava]
       :paasta-virhe-lapi? true}))

  PaivitaTarjoushinnatOnnistui
  (process-event [{:keys [vastaus paluukanava]} app]
    (viesti/nayta-toast! "Tarjoushinnat päivitetty" :onnistui)
    (go (>! paluukanava vastaus))
    (assoc app :tarjoushinnat vastaus))

  PaivitaTarjoushinnatEpaonnistui
  (process-event [{:keys [vastaus paluukanava]} app]
    (go (>! paluukanava false))
    (viesti/nayta-toast! "Tarjoushintojen päivitys epäonnistui" :varoitus)
    app)

  AsetaVainPuutteelliset
  (process-event [{:keys [vain-puutteelliset?]} app]
    (assoc app :vain-puutteelliset? vain-puutteelliset?))

  AsetaHaettavaUrakka
  (process-event [{:keys [teksti]} app]
    (assoc app :haettava-urakka teksti)))

