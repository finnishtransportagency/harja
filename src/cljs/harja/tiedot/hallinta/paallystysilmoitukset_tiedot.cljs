(ns harja.tiedot.hallinta.paallystysilmoitukset-tiedot
    "Päällystysilmoitusten tietojen hallinta"
    (:require [cljs.core.async :refer [>! <!]]
              [harja.loki :as log]
              [harja.ui.viesti :as viesti]
              [reagent.core :refer [atom] :as reagent]
              [tuck.core :as tuck]
              [harja.tyokalut.tuck :as tuck-apurit]
              [harja.pvm :as pvm]))

(defrecord HaePaallystysUrakat [vuosi])
(defrecord HaePaallystysUrakatOnnistui [vastaus])
(defrecord HaePaallystysUrakatEpaonnistui [vastaus])

(defrecord ValitseUrakka [urakka])
(defrecord ValitseUrakkaOnnistui [vastaus])
(defrecord ValitseUrakkaEpaonnistui [vastaus])

(def tila (atom {:valittu-urakka nil
                 :urakat nil
                 :urakan-paallystysilmoitukset nil}))

(extend-protocol tuck/Event
  HaePaallystysUrakat
    (process-event [{:keys [vuosi]} app]
      (tuck-apurit/post! :hae-paallystys-urakat-hallintaan
        {:vuosi 2024}
        {:onnistui ->HaePaallystysUrakatOnnistui
         :epaonnistui ->HaePaallystysUrakatEpaonnistui
         :paasta-virhe-lapi? true})
      (-> app
        (assoc :haku-kaynnissa? true)))
  HaePaallystysUrakatOnnistui
    (process-event [{:keys [vastaus]} app]
      (-> app
        (assoc :haku-kaynnissa? false)
        (assoc :urakat vastaus)))
  HaePaallystysUrakatEpaonnistui
    (process-event [{:keys [vastaus]} app]
      (viesti/nayta-toast! "haku epäonnistui" :varoitus)
      (assoc app :haku-kaynnissa? false))
  
  ValitseUrakka
  (process-event [{:keys [urakka]} app]
    (tuck-apurit/post! :urakan-paallystysilmoitukset
      {:urakka-id (:id urakka)
        :sopimus-id (:sopimus-id urakka)
        :vuosi 2024}
      {:onnistui ->ValitseUrakkaOnnistui
       :epaonnistui ->ValitseUrakkaEpaonnistui
       :paasta-virhe-lapi? true})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :valittu-urakka urakka)))

  ValitseUrakkaOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :urakan-paallystysilmoitukset vastaus)))

  ValitseUrakkaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false)))