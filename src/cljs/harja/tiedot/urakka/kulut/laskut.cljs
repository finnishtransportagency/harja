(ns harja.tiedot.urakka.kulut.laskut
  "Laskut tila"
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [reagent.ratom :refer [reaction]]))

;; Tilan määrittely
(defonce tila (atom {:nakymassa? false
                     :laskut nil
                     :haku-kaynnissa? false}))


;; Tapahtumien määrittely
(defrecord HaeLaskut [])
(defrecord HaeLaskutOnnistui [vastaus])
(defrecord HaeLaskutEpaonnistui [vastaus])

;; Tapahtumien käsittely
(extend-protocol tuck/Event
  HaeLaskut
  (process-event [_ app]
    (tuck-apurit/post! app :hae-kulut-laskut
      {}
      {:onnistui ->HaeLaskutOnnistui
       :epaonnistui ->HaeLaskutEpaonnistui})
    (assoc app :haku-kaynnissa? true))
  
  HaeLaskutOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :laskut vastaus
      :haku-käynnissä? false))
  
  HaeLaskutEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast!
      "Laskujen haku epäonnistui"
      :varoitus)
    (assoc app :haku-kaynnissa? false)))
