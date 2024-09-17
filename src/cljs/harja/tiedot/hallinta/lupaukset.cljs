(ns harja.tiedot.hallinta.lupaukset
  (:require [cljs.core.async :refer [>! <!]]
            [harja.loki :as log]
            [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defrecord HaeLupaustenLinkitykset [])
(defrecord HaeLupaustenLinkityksetOnnistui [vastaus])
(defrecord HaeLupaustenLinkityksetEpaonnistui [vastaus])

(defrecord HaeLupaustenKategoriat [])
(defrecord HaeLupaustenKategoriatOnnistui [vastaus])
(defrecord HaeLupaustenKategoriatEpaonnistui [vastaus])

(defrecord ValitseKategoria [kategoria])
(defrecord ValitseKategoriaOnnistui [vastaus])
(defrecord ValitseKategoriaEpaonnistui [vastaus])

(defrecord ValitseUrakka [urakka-id])
(defrecord ValitseUrakkaOnnistui [vastaus])
(defrecord ValitseUrakkaEpaonnistui [vastaus])

(def tila (atom {:valittu-urakka nil
                 :valittu-kategoria nil
                 :lupausten-linkitykset []
                 :haku-kaynnissa? false}))

(extend-protocol tuck/Event
  HaeLupaustenLinkitykset
  (process-event [_ app]
    (tuck-apurit/post! :hae-lupausten-linkitykset
      {}
      {:onnistui ->HaeLupaustenLinkityksetOnnistui
       :epaonnistui ->HaeLupaustenLinkityksetEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeLupaustenLinkityksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :lupausten-linkitykset vastaus))

  HaeLupaustenLinkityksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Urakoiden linkityksien haku epäonnistui" :varoitus)
    app)

  HaeLupaustenKategoriat
  (process-event [_ app]
    (tuck-apurit/post! :hae-rivin-tunnistin-selitteet
      {}
      {:onnistui ->HaeLupaustenKategoriatOnnistui
       :epaonnistui ->HaeLupaustenKategoriatEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeLupaustenKategoriatOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :lupausten-kategoriat vastaus))

  HaeLupaustenKategoriatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Urakoiden kategorioiden haku epäonnistui" :varoitus)
    app)

  ValitseKategoria
  (process-event [{:keys [kategoria]} app]
    (tuck-apurit/post! :hae-kategorian-urakat
      {:kategoria kategoria}
      {:onnistui ->ValitseKategoriaOnnistui
       :epaonnistui ->ValitseKategoriaEpaonnistui
       :paasta-virhe-lapi? true})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :valittu-kategoria kategoria)))

  ValitseKategoriaOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :kategorian-urakat vastaus)))

  ValitseKategoriaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Urakoiden haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false))

  ValitseUrakka
  (process-event [{:keys [urakka-id]} app]
    (tuck-apurit/post! :hae-urakan-lupaukset
      {:urakka-id (:urakka_id urakka-id)}
      {:onnistui ->ValitseUrakkaOnnistui
       :epaonnistui ->ValitseUrakkaEpaonnistui
       :paasta-virhe-lapi? true})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :valittu-urakka urakka-id)))

  ValitseUrakkaOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :urakan-lupaukset vastaus)))

  ValitseUrakkaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Lupausten haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false)))