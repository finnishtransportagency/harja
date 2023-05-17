(ns harja.tiedot.tyomaapaivakirja
  "Työmaapäiväkirja kutsut"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]))

(defonce tila (atom {:tiedot []
                     :nayta-rivit []
                     :valittu-rivi nil
                     :viimeksi-valittu nil
                     :valinnat {:aikavali (pvm/paivamaaran-hoitokausi (pvm/nyt))
                                :hakumuoto :kaikki}}))

(def suodattimet {:kaikki nil
                  :myohastyneet 1
                  :puuttuvat 2})

(defrecord HaeTiedot[])
(defrecord ValitseRivi[rivi])
(defrecord PoistaRiviValinta[])
(defrecord PaivitaAikavali[uudet])
(defrecord PaivitaHakumuoto[uudet])
(defrecord HaeTiedotOnnistui[vastaus])
(defrecord HaeTiedotEpaonnistui[vastaus])

(defn suodata-rivit-aikavalilla [valinnat]
  ;; Annetaan parametrina tilan :valinnat josta luetaan hakumuoto & valittu aikaväli
  (let [rivit (filter (fn [rivi]
                        (let [tyopaiva (:alkupvm rivi)
                              aikavali (:aikavali valinnat)
                              valittu-hakumuoto (get suodattimet (-> @tila :valinnat :hakumuoto))
                              rivin-toimitustila (:tila rivi)]
                          (and
                            ;; Onko "TYÖPÄIVÄ"- sarake valitun aikavälin välissä
                            (pvm/valissa? tyopaiva (first aikavali) (second aikavali))
                            (or
                              ;; Vastaako valittu hakumuoto rivin toimituksen tilaa 
                              (= valittu-hakumuoto nil)
                              (= valittu-hakumuoto rivin-toimitustila))))) (:tiedot @tila))]
    rivit))

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
      (assoc :nayta-rivit vastaus)))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeTiedotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeTiedotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  PaivitaAikavali
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app) u)]
      (-> app
        (assoc :valinnat uudet-valinnat)
        (assoc :nayta-rivit (suodata-rivit-aikavalilla uudet-valinnat)))))

  PaivitaHakumuoto
  (process-event [u app]
    (let [uudet-valinnat (:uudet u)]
      (-> app
        (assoc-in [:valinnat :hakumuoto] uudet-valinnat)
        (assoc :nayta-rivit (suodata-rivit-aikavalilla (-> @tila :valinnat))))))

  ValitseRivi
  (process-event [{rivi :rivi} app]
    (-> app
      (assoc :valittu-rivi rivi)
      (assoc :viimeksi-valittu rivi)))

  PoistaRiviValinta
  (process-event [_ app]
    (-> app
      (assoc :valittu-rivi nil))))
