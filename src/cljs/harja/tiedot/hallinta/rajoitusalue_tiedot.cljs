(ns harja.tiedot.hallinta.rajoitusalue-tiedot
  "Rajoitusalueiden pituuksien laskenta ja korjaus ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.loki :refer [log]]))

(def nakymassa? (atom false))

(def data (atom {}))
(defrecord HaeRajoitusalueet [])
(defrecord HaeRajoitusalueetOnnistui [vastaus])
(defrecord HaeRajoitusalueetEpaonnistui [vastaus])

(defn- hae-rajoitusalueet []
  (tuck-apurit/post! :hae-rajoitusalueiden-pituudet
    {}
    {:onnistui ->HaeRajoitusalueetOnnistui
     :epaonnistui ->HaeRajoitusalueetEpaonnistui
     :paasta-virhe-lapi? true}))

(extend-protocol tuck/Event

  ;; Haetaan urakat, joilla on olemassa pohjavesialueille tehtyjä rajoituksia
  HaeRajoitusalueet
  (process-event [_ app]
    (let [_ (hae-rajoitusalueet)]
      (assoc app :rajoitushaku-kaynnissa? true)))

  HaeRajoitusalueetOnnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeRajoitusalueetOnnistui :: vastaus" (pr-str vastaus))
    (-> app
      (assoc :rajoitusalueet vastaus)
      (assoc :rajoitushaku-kaynnissa? false)))

  HaeRajoitusalueetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaeRajoitusalueetEpäonnistui :: vastaus" (pr-str vastaus))
      (-> app
        (assoc :rajoitusalueet [])
        (assoc :rajoitushaku-kaynnissa? false))))


  )
