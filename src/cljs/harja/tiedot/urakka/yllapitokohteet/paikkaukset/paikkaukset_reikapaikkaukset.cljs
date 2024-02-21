(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset
  "Reikäpaikkaukset- tiedot"
  ;; TODO.. lisätty valmiiksi requireja, poista myöhemmin turhat 
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

;; vars
(defonce tila (atom {:rivit nil
                     :valittu-rivi nil
                     :valinnat {:aikavali (pvm/kuukauden-aikavali (pvm/nyt))
                                :tr-osoite nil}}))

(def nakymassa? (atom false))
(def aikavali-atom (atom (pvm/kuukauden-aikavali (pvm/nyt))))

;; tuck 
(defrecord PaivitaAikavali [uudet])
(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])


;; funktiot
(defn- hae-reikapaikkaukset [app]
  (tuck-apurit/post! app :hae-reikapaikkaukset
    {:urakka-id (:id @nav/valittu-urakka)}
    ;; callback
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

  PaivitaAikavali
  (process-event [{uudet :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app) uudet)
          app (assoc app :valinnat uudet-valinnat)]
      ;; do stuff
      (println "Päivitettiin valinnat: " uudet)
      app)))
