(ns harja.tiedot.hallinta.pohjavesialueidensiirto-tiedot
  "Pohjavesialueidensiirron ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [cljs.core.async :refer [<! >! chan close!]]
            [cljs-http.client :as http]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.viesti :as viesti]
            [harja.loki :refer [log]]
            [harja.tiedot.hallinta.yhteiset :as yhteiset])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def data (atom {}))
(defrecord HaePohjavesialueurakat [])
(defrecord HaePohjavesialueurakatOnnistui [vastaus])
(defrecord HaePohjavesialueurakatEpaonnistui [vastaus])

(defrecord HaeUrakanPohjavesialueet [urakkaid])
(defrecord HaeUrakanPohjavesialueetOnnistui [vastaus])
(defrecord HaeUrakanPohjavesialueetEpaonnistui [vastaus])


(defrecord TeeSiirto [urakka])
(defrecord TeeSiirtoOnnistui [vastaus])
(defrecord TeeSiirtoEpaonnistui [vastaus])

(extend-protocol tuck/Event

  ;; Haetaan urakat, joilla on olemassa pohjavesialueille tehtyjä rajoituksia
  HaePohjavesialueurakat
  (process-event [_ app]
    (let [_ (tuck-apurit/post! :hae-pohjavesialueurakat
              {}
              {:onnistui ->HaePohjavesialueurakatOnnistui
               :epaonnistui ->HaePohjavesialueurakatEpaonnistui
               :paasta-virhe-lapi? true})]
      (assoc app :urakkahaku-kaynnissa? true)))

  HaePohjavesialueurakatOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaePohjavesialueurakatOnnistui :: vastaus" (pr-str vastaus))
      (-> app
        (assoc :urakat vastaus)
        (assoc :urakkahaku-kaynnissa? false))))

  HaePohjavesialueurakatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaePohjavesialueurakatEpaonnistui :: vastaus" (pr-str vastaus))
      (assoc app :urakkahaku-kaynnissa? false)))

  ;; HAe valitun urakan pohjavesialueet, joilla on rajoituksia siinä muodossa, kun ne muokattaessa tulee rajoitusalueiksi
  HaeUrakanPohjavesialueet
  (process-event [{urakkaid :urakkaid} app]
    (let [_ (tuck-apurit/post! :hae-urakan-siirrettavat-pohjavesialueet
              {:urakkaid urakkaid}
              {:onnistui ->HaeUrakanPohjavesialueetOnnistui
               :epaonnistui ->HaeUrakanPohjavesialueetEpaonnistui
               :paasta-virhe-lapi? true})]
      (-> app
        (assoc :valittu-urakka urakkaid)
        (assoc :pohjavesialuehaku-kaynnissa? true))))

  HaeUrakanPohjavesialueetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [urakkaid (:valittu-urakka app)
          ;; Muokataan formiaattitiedot
          vastaus (map #(if (= 0 (:talvisuolaraja %))
                          (assoc % :formiaatti true)
                          (assoc % :formiaatti false)
                          ) vastaus)
          _ (js/console.log "HaeUrakanPohjavesialueetOnnistui :: urakkaid" (pr-str urakkaid))]
      (do
        (js/console.log "HaeUrakanPohjavesialueetOnnistui :: vastaus" (pr-str vastaus))
        (-> app
          (update :urakat (fn [urakat]
                            (do
                              (map (fn [urakka]
                                     (do
                                       (js/console.log "HaeUrakanPohjavesialueetOnnistui :: urakka: onko " urakkaid "==" (pr-str urakka))
                                       (if (= urakkaid (:id urakka))
                                           (assoc urakka :pohjavesialueet vastaus)
                                           urakka)))
                                urakat))))
          (assoc :pohjavesialueet vastaus)
          (assoc :pohjavesialuehaku-kaynnissa? false)))))

  HaeUrakanPohjavesialueetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :pohjavesialuehaku-kaynnissa? false))

  TeeSiirto
  (process-event [{urakka :urakka} app]
    (let [_ (js/console.log "TeeSiirto :: urakka" (pr-str urakka))
          _ (js/console.log "TeeSiirto :: app" (pr-str app))
          pohjavesialueet (:pohjavesialueet urakka) #_ (some
                             (fn [u]
                               (do
                                 (js/console.log "some: u" (pr-str u) "urakkaid: " (pr-str (:id urakka)))
                                 (when (= (:id urakka) (:urakkaid u))
                                   (:pohjavesialueet u))))
                             (:urakat app))
          _ (js/console.log "TeeSiirto :: pohjavesialueet" (pr-str pohjavesialueet))
          _ (tuck-apurit/post! :siirra-urakan-pohjavesialueet
              {:urakkaid (:id urakka)
               :pohjavesialueet pohjavesialueet}
              {:onnistui ->TeeSiirtoOnnistui
               :epaonnistui ->TeeSiirtoEpaonnistui
               :paasta-virhe-lapi? true})]
      (assoc app :siirto-kaynnissa? true)))

  TeeSiirtoOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "TeeSiirtoOnnistui :: vastaus" (pr-str vastaus))
      (-> app
        (assoc :jotain vastaus)
        (assoc :siirto-kaynnissa? false))))

  TeeSiirtoEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "TeeSiirtoEpaonnistui :: vastaus" (pr-str vastaus))
      (assoc app :siirto-kaynnissa? false)))

  )
