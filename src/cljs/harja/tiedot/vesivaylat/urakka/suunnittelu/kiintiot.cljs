(ns harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [harja.tyokalut.tuck :as tuck]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]))

(defonce app (r/atom {:nakymassa? false
                      :kiintioiden-haku-kaynnissa? false
                      :kiintiot nil}))

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeKiintiot [])
(defrecord KiintiotHaettu [tulos])
(defrecord KiintiotEiHaettu [])

(extend-protocol t/Event
  Nakymassa?
  (process-event [{nak :nakymassa?} app]
    (assoc app :nakymassa? nak))

  HaeKiintiot
  (process-event [_ app]
    (if-not (:kiintioiden-haku-kaynnissa? app)
      (let [parametrit {}]
        (-> app
           (tuck/palvelukutsu :hae-kiintiot
                              parametrit
                              {:onnistui ->KiintiotHaettu
                               :epaonnistui ->KiintiotEiHaettu})
           (assoc :kiintioiden-haku-kaynnissa? true)))

      app))

  KiintiotHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :kiintioiden-haku-kaynnissa? false
               :kiintiot tulos))

  KiintiotEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kiintiöiden haku epäonnistui!" :danger)
    (assoc app :kiintioiden-haku-kaynnissa? false)))

