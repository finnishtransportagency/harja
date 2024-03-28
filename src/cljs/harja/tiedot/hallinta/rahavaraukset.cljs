(ns harja.tiedot.hallinta.rahavaraukset
  (:require [cljs.core.async :refer [>! <!]]
            [harja.loki :as log]
            [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:valittu-urakka nil
                 :rahavaraukset nil
                 :tehtavat nil}))

(defrecord HaeRahavaraukset [])
(defrecord HaeRahavarauksetOnnistui [vastaus])
(defrecord HaeRahavarauksetEpaonnistui [vastaus])
(defrecord HaeUrakoidenRahavaraukset [])
(defrecord HaeUrakoidenRahavarauksetOnnistui [vastaus])
(defrecord HaeUrakoidenRahavarauksetEpaonnistui [vastaus])
(defrecord ValitseUrakanRahavaraus [urakka rahavaraus valittu?])


(defrecord ValitseUrakka [urakka])

(extend-protocol tuck/Event
  HaeRahavaraukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-rahavaraukset
      {}
      {:onnistui ->HaeRahavarauksetOnnistui
       :epaonnistui ->HaeRahavarauksetEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeRahavarauksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :rahavaraukset vastaus))

  HaeRahavarauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksien haku epäonnistui" :varoitus)
    app)

  HaeUrakoidenRahavaraukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakoiden-rahavaraukset
      {}
      {:onnistui ->HaeUrakoidenRahavarauksetOnnistui
       :epaonnistui ->HaeUrakoidenRahavarauksetEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeUrakoidenRahavarauksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :urakoiden-rahavaraukset vastaus))

  HaeUrakoidenRahavarauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Urakoiden rahavarauksien haku epäonnistui" :varoitus)
    app)

  ValitseUrakanRahavaraus
  (process-event [{:keys [urakka rahavaraus valittu?]} app]
    (let []
      (println urakka rahavaraus valittu?)
      app))

  ValitseUrakka
  (process-event [{:keys [urakka]} app]
    (assoc app :valittu-urakka urakka)))
