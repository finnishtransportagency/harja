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
                 :alukset nil
                 :urakoitsijoiden-haku-kaynnissa? false
                 :alusten-tallennus-kaynnissa? false}))

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeAlukset [])
(defrecord AluksetHaettu [tulos])
(defrecord AluksetEiHaettu [])
(defrecord HaeUrakoitsijat [])
(defrecord UrakoitsijatHaettu [tulos])
(defrecord UrakoitsijatEiHaettu [])
(defrecord TallennaAlukset [alukset])
(defrecord AluksetTallennettu [tulos])
(defrecord AluksetEiTallennettu [])

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nak :nakymassa?} app]
    (assoc app :nakymassa? nak))

  HaeAlukset
  (process-event [_ app]
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
               :alukset []))

  HaeUrakoitsijat
  (process-event [_ app]
    (if (not (:urakoitsijoiden-haku-kaynnissa? app))
      (-> app
          (tuck-apurit/palvelukutsu :vesivaylaurakoitsijat
                                    {}
                                    {:onnistui ->UrakoitsijatHaettu
                                     :epaonnistui ->UrakoitsijatEiHaettu})
          (assoc :urakoitsijoiden-haku-kaynnissa? true))

      app))

  UrakoitsijatHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :urakoitsijoiden-haku-kaynnissa? false
               :urakoitsijat tulos))

  UrakoitsijatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Urakoitsijoiden haku epäonnistui!" :danger)
    (assoc app :urakoitsijoiden-haku-kaynnissa? false
               :urakoitsijat []))

  TallennaAlukset
  (process-event [{alukset :alukset} app]
    (if (not (:alusten-tallennus-kaynnissa? app))
      (-> app
          (tuck-apurit/palvelukutsu :tallenna-alukset
                                    {::alus/tallennettavat-alukset alukset}
                                    {:onnistui ->AluksetTallennettu
                                     :epaonnistui ->AluksetEiTallennettu})
          (assoc :alusten-tallennus-kaynnissa? true))

      app))

  AluksetTallennettu
  (process-event [{tulos :tulos} app]
    (assoc app :alusten-tallennus-kaynnissa? false
               :alukset tulos))

  AluksetEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Alusten tallennus epäonnistui!" :danger)
    (assoc app :alusten-tallennus-kaynnissa? false
               :alukset [])))