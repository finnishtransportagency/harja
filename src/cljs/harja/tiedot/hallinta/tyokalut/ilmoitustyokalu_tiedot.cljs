(ns harja.tiedot.hallinta.tyokalut.ilmoitustyokalu-tiedot
  "Ilmoitustyökalun ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]))


(def alkutila {:ilmoitus {:nakymassa? false}
               :lahetys-kaynnissa? false})
(def data (atom alkutila))
(def nakymassa? (atom false))

(defrecord Muokkaa [ilmoitus])

(defrecord Laheta [ilmoitus])
(defrecord LahetysOnnistui [vastaus])
(defrecord LahetysEpaonnistui [vastaus])

(extend-protocol tuck/Event

  Muokkaa
  (process-event [{ilmoitus :ilmoitus} app]
    (assoc app :ilmoitus ilmoitus))

  Laheta
  (process-event [{ilmoitus :ilmoitus} app]
    (let [xml (:xml ilmoitus)
          _ (js/console.log "xml" (pr-str xml))]
      (tuck-apurit/post! :debug-ilmoitus-xml
        {:xml xml}
        {:onnistui ->LahetysOnnistui
         :epaonnistui ->LahetysEpaonnistui
         :paasta-virhe-lapi? true})
      (assoc app :lahetys-kaynnissa? true)))

  LahetysOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Ilmoitus lähetetty" :onnistui)
      (js/console.log "Vastaus: " (pr-str vastaus))
      (assoc app :lahetys-kaynnissa? false)))

  LahetysEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Ilmoituksen lähetys epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    (assoc app :lahetys-kaynnissa? false)))
