(ns harja.tiedot.hallinta.jarjestelma-asetukset
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom] :as r]
            [harja.domain.geometriaaineistot :as geometria-ainestot]
            [harja.pvm :as pvm]
            [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :as async]
            [harja.ui.viesti :as viesti])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
         (atom {:nakymassa? false
                :geometria-aineistot nil
                :haku-kaynnissa? false}))

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeGeometria-aineistot [])
(defrecord Geometria-aineistotHaettu [geometria-aineistot])
(defrecord Geometria-ainestojenHakuEpaonnistui [])

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeGeometria-aineistot
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->Geometria-aineistotHaettu)
          fail! (tuck/send-async! ->Geometria-ainestojenHakuEpaonnistui)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :hae-geometria-aineistot {}))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :haku-kaynnissa? true))

  Geometria-aineistotHaettu
  (process-event [{geometria-aineistot :geometria-aineistot} app]
    (assoc app :geometria-aineistot geometria-aineistot
               :haku-kaynnissa? false))

  Geometria-ainestojenHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe geometria-aineistojen haussa!"] :danger)
    (assoc app :haku-kaynnissa? false)))