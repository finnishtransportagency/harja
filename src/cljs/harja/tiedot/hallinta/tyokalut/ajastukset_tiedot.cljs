(ns harja.tiedot.hallinta.tyokalut.ajastukset-tiedot
  "Ajastusten ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]))

(def tila (atom {:kustannusarvioidut-tyot-toteumiksi-kaynnissa false}))

(defrecord AjaKustannusarvioidutTyotToteumiksi [])
(defrecord AjaKustannusarvioidutTyotToteumiksiOnnistui [vastaus])
(defrecord AjaKustannusarvioidutTyotToteumiksiEpaonnistui [vastaus])

(defrecord AjaKasitteleUrakatMaksueriksi [])
(defrecord AjaKasitteleUrakatMaksueriksiOnnistui [vastaus])
(defrecord AjaKasitteleUrakatMaksueriksiEpaonnistui [vastaus])

(extend-protocol tuck/Event

  AjaKustannusarvioidutTyotToteumiksi
  (process-event [_ app]
    (js/console.log "AjaKustannusarvioidutTyotToteumiksi")
    (tuck-apurit/post! :aja-kustannusarviot-toteumiksi
      {}
      {:onnistui ->AjaKustannusarvioidutTyotToteumiksiOnnistui
       :epaonnistui ->AjaKustannusarvioidutTyotToteumiksiEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :kustannusarvioidut-tyot-toteumiksi-kaynnissa true))

  AjaKustannusarvioidutTyotToteumiksiOnnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "AjaKustannusarvioidutTyotToteumiksiOnnistui")
    (viesti/nayta-toast! "Kustannusarvioidut työt ajettu toteumiksi!" :onnistui)
    (assoc app :kustannusarvioidut-tyot-toteumiksi-kaynnissa false))

  AjaKustannusarvioidutTyotToteumiksiEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Ajo epäonnistui!" :varoitus viesti/viestin-nayttoaika-pitka)
    (assoc app :kustannusarvioidut-tyot-toteumiksi-kaynnissa false))

  AjaKasitteleUrakatMaksueriksi
  (process-event [_ app]
    (js/console.log "AjaKasitteleUrakatMaksueriksi")
    (tuck-apurit/post! :aja-kasittele-maksuerat
      {}
      {:onnistui ->AjaKasitteleUrakatMaksueriksiOnnistui
       :epaonnistui ->AjaKasitteleUrakatMaksueriksiEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :maksuerat-kaynnissa true))

  AjaKasitteleUrakatMaksueriksiOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "AjaKasitteleUrakatMaksueriksiOnnistui")
      (viesti/nayta-toast! "Maksuerät alustettu!" :onnistui)
      (assoc app :maksuerat-kaynnissa false)))

  AjaKasitteleUrakatMaksueriksiEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Ajo epäonnistui!" :varoitus viesti/viestin-nayttoaika-pitka)
      (assoc app :maksuerat-kaynnissa false))))
