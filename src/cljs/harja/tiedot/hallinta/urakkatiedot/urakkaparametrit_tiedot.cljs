(ns harja.tiedot.hallinta.urakkatiedot.urakkaparametrit-tiedot
  "Urakkaparametrien ui controlleri. Näyttää valitun urakan urakka_parametrit-taulun rivin tiedot vain lukutilassa."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [taoensso.timbre :as log]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom {:urakat []
                 :valittu-urakka nil
                 :parametrit nil
                 :urakat-haku-kaynnissa? false
                 :parametrit-haku-kaynnissa? false}))

(defrecord HaeUrakat [])
(defrecord HaeUrakatOnnistui [vastaus])
(defrecord HaeUrakatEpaonnistui [vastaus])

(defrecord ValitseUrakka [urakka])

(defrecord HaeUrakanParametritOnnistui [vastaus])
(defrecord HaeUrakanParametritEpaonnistui [vastaus])

(extend-protocol tuck/Event
  HaeUrakat
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakkaparametrit-urakat {}
      {:onnistui ->HaeUrakatOnnistui
       :epaonnistui ->HaeUrakatEpaonnistui})
    (assoc app :urakat-haku-kaynnissa? true))

  HaeUrakatOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :urakat-haku-kaynnissa? false)
      (assoc :urakat (sort-by :nimi vastaus))))

  HaeUrakatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (log/error "Urakoiden haku epäonnistui hallinnan urakan parametrit -näkymässä. Virhe: " vastaus)
    (viesti/nayta-toast! "Urakoiden haku epäonnistui." :varoitus)
    (assoc app :urakat-haku-kaynnissa? false))

  ValitseUrakka
  (process-event [{:keys [urakka]} app]
    (let [urakkaid (:id urakka)]
      (when urakkaid
        (tuck-apurit/post! :hae-urakan-parametrit {:urakkaid urakkaid}
          {:onnistui ->HaeUrakanParametritOnnistui
           :epaonnistui ->HaeUrakanParametritEpaonnistui}))
      (-> app
        (assoc :valittu-urakka urakka)
        (assoc :parametrit nil)
        (assoc :parametrit-haku-kaynnissa? (boolean urakkaid)))))

  HaeUrakanParametritOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :parametrit-haku-kaynnissa? false)
      (assoc :parametrit vastaus)))

  HaeUrakanParametritEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (log/error "Urakan parametrien haku epäonnistui. Virhe: " vastaus)
    (viesti/nayta-toast! "Urakan parametrien haku epäonnistui." :varoitus)
    (assoc app :parametrit-haku-kaynnissa? false)))
