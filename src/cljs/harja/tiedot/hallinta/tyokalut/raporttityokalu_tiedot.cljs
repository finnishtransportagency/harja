(ns harja.tiedot.hallinta.tyokalut.raporttityokalu-tiedot
  "Ajastusten ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]))

(def tila (atom {:paivita-materiaalicache-kaynnissa false}))

(defrecord PaivitaMateriaalicachetUrakalle [])
(defrecord PaivitaMateriaalicachetUrakalleOnnistui [vastaus])
(defrecord PaivitaMateriaalicachetUrakalleEpaonnistui [vastaus])

;; Täydennä alla olevat defrecordeiksi
(defrecord AsetaUrakkaId [urakka-id])
(defrecord AsetaAlkupvm [alkupvm])
(defrecord AsetaLoppupvm [loppupvm])

(extend-protocol tuck/Event

  AsetaUrakkaId
  (process-event [{urakka-id :urakka-id} app]
    (assoc app :urakka-id urakka-id))

  AsetaAlkupvm
  (process-event [{alkupvm :alkupvm} app]
    (assoc app :alkupvm alkupvm))

  AsetaLoppupvm
  (process-event [{loppupvm :loppupvm} app]
    (assoc app :loppupvm loppupvm))

  PaivitaMateriaalicachetUrakalle
  (process-event [_ app]
    (tuck-apurit/post! :paivita-materiaalicachet-urakalle
      {:urakka-id (:urakka-id app)
       :alkupvm (:alkupvm app)
       :loppupvm (:loppupvm app)}
      {:onnistui ->PaivitaMateriaalicachetUrakalleOnnistui
       :epaonnistui ->PaivitaMateriaalicachetUrakalleEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :paivita-materiaalicache-kaynnissa true))

  PaivitaMateriaalicachetUrakalleOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Materiaalicachet päivitetty" :onnistui)
    (assoc app :paivita-materiaalicache-kaynnissa false))

  PaivitaMateriaalicachetUrakalleEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Päivitys epäonnistui!" :varoitus viesti/viestin-nayttoaika-pitka)
    (assoc app :paivita-materiaalicache-kaynnissa false)))
