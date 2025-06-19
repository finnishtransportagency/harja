(ns harja.tiedot.hallinta.tyokalut.tieosoitteet-tyokalu-tiedot
  "Tieosoitteiden ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]))

(def tila (atom {:tieosoitteet nil
                 :haku-kaynnissa? false}))

(defrecord HaeTieosoitteet [])
(defrecord HaeTieosoitteetOnnistui [vastaus])
(defrecord HaeTieosoitteetEpaonnistui [vastaus])
(defrecord FiltteroiTienumerolla [tie])

(defn filteroi-tieosoitteet [tieosoitteet tie]
  (filter #(= tie (:tie %)) tieosoitteet))

(extend-protocol tuck/Event

  HaeTieosoitteet
  (process-event [_ app]
    (js/console.log "HaeTieosoitteet")
    (tuck-apurit/post! :hae-tieosoitteet-hallintaan
      {}
      {:onnistui ->HaeTieosoitteetOnnistui
       :epaonnistui ->HaeTieosoitteetEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :haku-kaynnissa? true))

  HaeTieosoitteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Tieosoitteet haettu" :onnistui)
      (-> app
        (assoc :tieosoitteet vastaus)
        (assoc :filtteroidyt-tieosoitteet (if (get-in app [:filtterit :tie])
                                            (filteroi-tieosoitteet vastaus (get-in app [:filtterit :tie]))
                                            vastaus))
        (assoc :haku-kaynnissa? false))))

  HaeTieosoitteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Tieosoitteiden haku epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (-> app
        (assoc :tieosoitteet nil)
        (assoc :filtteroidyt-tieosoitteet nil)
        (assoc :haku-kaynnissa? false))))

  FiltteroiTienumerolla
  (process-event [{tie :tie} app]
    (assoc app :filtteroidyt-tieosoitteet (filteroi-tieosoitteet (:tieosoitteet app) tie))))
