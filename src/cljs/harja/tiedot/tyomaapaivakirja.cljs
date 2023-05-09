(ns harja.tiedot.tyomaapaivakirja
  "Työmaapäiväkirja kutsut"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]))

(defonce tila (atom {:tiedot []
                     :valitut-rivit []}))

(def suodattimet {:kaikki nil
                  :myohastyneet 1
                  :puuttuvat 2})

(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])

(defn suodata-rivit [valinta]
  (let [items (filter (fn [rivi]
                        ;; Tietokanta palauttaa mock dataa random numeron 0-2
                        ;; Rivin valinta palauttaa avaimen (:kaikki / :myohastyneet / :puuttuvat)
                        ;; Verrataan tietokannan palauttamaa lukua ja avaimia
                        (let [toimituksen-tila (:tila rivi)
                              suodattimet (get suodattimet valinta)]
                          (or
                            ;; Palauta valitut tulokset tai kaikki
                            (= suodattimet toimituksen-tila)
                            (nil? suodattimet)))) (:tiedot @tila))
        _ (-> tila
            (swap! assoc :valitut-rivit items))]))

(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (tuck-apurit/post! app :tyomaapaivakirja-hae
      {}
      {:onnistui ->HaeTiedotOnnistui
       :epaonnistui ->HaeTiedotEpaonnistui}))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :tiedot vastaus)
      (assoc :valitut-rivit vastaus)))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeTiedotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeTiedotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app))
