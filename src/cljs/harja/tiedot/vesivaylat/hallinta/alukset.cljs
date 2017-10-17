(ns harja.tiedot.vesivaylat.hallinta.alukset
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [tuck.core :as t]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(def tila (atom {:nakymassa? false
                 :alusten-haku-kaynnissa? false
                 :alukset nil}))

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeAlukset [])
(defrecord AluksetHaettu [tulos])
(defrecord AluksetEiHaettu [])

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nak :nakymassa?} app]
    (assoc app :nakymassa? nak))

  HaeAlukset
  (process-event [{valinnat :valinnat} app]
    (if (not (:alusten-haku-kaynnissa? app))
      (-> app
          (tuck-apurit/palvelukutsu :hae-kaikki-alukset
                                    {}
                                    {:onnistui ->AluksetHaettu
                                     :epaonnistui ->AluksetEiHaettu})
          (assoc :alusten-haku-kaynnissa? true))

      app))

  AluksetHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :alusten-haku-kaynnissa? false
               :alukset tulos))

  AluksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Alusten haku epäonnistui!" :danger)
    (assoc app :alusten-haku-kaynnissa? false
               :alukset [])))