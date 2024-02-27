(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset
  "Reikäpaikkaukset- tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:rivit nil
                     :valittu-rivi nil
                     :muokataan false
                     :valinnat {:aikavali (pvm/kuukauden-aikavali (pvm/nyt))
                                :tr-osoite nil}}))

(def nakymassa? (atom false))
(def aikavali-atom (atom (pvm/kuukauden-aikavali (pvm/nyt))))

;; Kartta jutskat
(defonce valittu-reikapaikkaus (atom nil))
(def karttataso-reikapaikkaukset (atom false))

;; Kartalle hakufunktio
(defn hae-urakan-reikapaikkaukset [urakka-id]
  (k/post! :hae-reikapaikkaukset {:urakka-id urakka-id}))

;; Tulokset reaction
(defonce haetut-reikapaikkaukset
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?]
    {:nil-kun-haku-kaynnissa? true}
    (when nakymassa?
      (hae-urakan-reikapaikkaukset urakka-id)))) 

;; Karttataso
(defonce reikapaikkaukset-kartalla
  (reaction
    (let [valittu-id 36]
      (when @karttataso-reikapaikkaukset
        (kartalla-esitettavaan-muotoon
          @haetut-reikapaikkaukset
          #(= valittu-id (:id %))
          (comp
            (keep #(and (:sijainti %) %))
            (map #(assoc % :tyyppi-kartalla :reikapaikkaus))))))))

;; Tuck 
(defrecord PaivitaAikavali [uudet])
(defrecord HaeTiedot [])
(defrecord AvaaMuokkausModal [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])


;; Funktiot
(defn- hae-reikapaikkaukset [app]
  (tuck-apurit/post! app :hae-reikapaikkaukset
    {:urakka-id (:id @nav/valittu-urakka)}
    ;; Callback
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(extend-protocol tuck/Event

  HaeTiedot
  (process-event [_ app]
    (println "call hae tiedot ")
    (hae-reikapaikkaukset app)
    app)

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (assoc app :rivit vastaus)]
      (println "tiedot onnistui")
      app))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeTiedotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeTiedotEpaonnistui Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  AvaaMuokkausModal
  (process-event [_ app]
    (assoc app :muokataan true))

  PaivitaAikavali
  (process-event [{uudet :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app) uudet)
          app (assoc app :valinnat uudet-valinnat)]
      ;; do stuff
      (println "Päivitettiin valinnat: " uudet)
      app)))
