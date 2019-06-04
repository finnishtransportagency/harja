(ns harja.tiedot.hallinta.jarjestelma-asetukset
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom] :as r]
            [harja.domain.geometriaaineistot :as geometria-ainestot]
            [harja.pvm :as pvm]
            [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :as async]
            [harja.ui.viesti :as viesti]
            [harja.loki :refer [log]]
            [harja.tyokalut.tuck :as tuck-apurit])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
  (atom {:nakymassa? false
         :geometria-aineistot nil
         :haku-kaynnissa? false
         :tallennus-kaynnissa? false}))

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeGeometria-aineistot [])
(defrecord Geometria-aineistotHaettu [geometria-aineistot])
(defrecord Geometria-ainestojenHakuEpaonnistui [])
(defrecord TallennaGeometria-ainestot [geometria-aineistot paluukanava])
(defrecord Geometria-aineistotTallennettu [geometria-aineistot paluukanava])
(defrecord Geometria-ainestojenTallennusEpaonnistui [geometria-aineistot paluukanava])

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeGeometria-aineistot
  (process-event [_ app]
    (let [onnistui! (tuck/send-async! ->Geometria-aineistotHaettu)
          virhe! (tuck/send-async! ->Geometria-ainestojenHakuEpaonnistui)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :hae-geometria-aineistot {}))]
            (if (k/virhe? vastaus)
              (virhe! vastaus)
              (onnistui! vastaus)))
          (catch :default e
            (virhe! nil)
            (throw e)))))
    (assoc app :haku-kaynnissa? true))

  Geometria-aineistotHaettu
  (process-event [{geometria-aineistot :geometria-aineistot} app]
    (assoc app :geometria-aineistot geometria-aineistot
               :haku-kaynnissa? false))

  Geometria-ainestojenHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe geometria-aineistojen haussa!"] :danger)
    (assoc app :haku-kaynnissa? false))

  TallennaGeometria-ainestot
  (process-event [{geometria-aineistot :geometria-aineistot ch :paluukanava} app]
    (if-not (:tallennus-kaynnissa? app)
      (-> app
          (tuck-apurit/post! :tallenna-geometria-aineistot
                             geometria-aineistot
                             {:onnistui ->Geometria-aineistotTallennettu
                              :onnistui-parametrit [ch]
                              :epaonnistui ->Geometria-ainestojenTallennusEpaonnistui
                              :epaonnistui-parametrit [ch]})
          (assoc :tallennus-kaynnissa? true))
      app))

  Geometria-aineistotTallennettu
  (process-event [{geometria-aineistot :geometria-aineistot ch :paluukanava} app]
    (viesti/nayta! [:span "Geometria-aineistot tallennettu"] :success)
    (go (>! ch geometria-aineistot))
    (assoc app :geometria-aineistot geometria-aineistot
               :tallennus-kaynnissa? false))

  Geometria-ainestojenTallennusEpaonnistui
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe geometria-aineistojen tallentamisessa!"] :danger)
    (assoc app :tallennus-kaynnissa? false)))
