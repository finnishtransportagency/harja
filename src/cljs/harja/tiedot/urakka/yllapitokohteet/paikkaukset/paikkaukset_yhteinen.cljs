(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen
  (:require [reagent.core :refer [atom]]
            [clojure.data :refer [diff]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [harja.ui.viesti :as viesti]
            [cljs.core.async :refer [<!]]))

;; Työmenetelmät
(defrecord HaeTyomenetelmat [])
(defrecord HaeTyomenetelmatOnnistui [vastaus])
(defrecord HaeTyomenetelmatEpaonnistui [vastaus])

(extend-protocol tuck/Event

  HaeTyomenetelmat
  (process-event [_ app]
    (do (tuck-apurit/post! app
                           :hae-paikkauskohteiden-tyomenetelmat
                           {}
                           {:onnistui ->HaeTyomenetelmatOnnistui
                            :epaonnistui ->HaeTyomenetelmatEpaonnistui
                            :paasta-virhe-lapi? true})
        app))

  HaeTyomenetelmatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tyomenetelmat vastaus))

  HaeTyomenetelmatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Työmenetelmien haku epäonnistui, vastaus " (pr-str vastaus))
      (viesti/nayta-toast! "Paikkauskohteiden tyomenetelmien haku epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
      app)))