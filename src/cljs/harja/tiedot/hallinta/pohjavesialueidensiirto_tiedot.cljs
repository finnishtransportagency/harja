(ns harja.tiedot.hallinta.pohjavesialueidensiirto-tiedot
  "Pohjavesialueidensiirron ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.loki :refer [log]]))

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

(defn- hae-pohjavesialueiden-urakat []
  (tuck-apurit/post! :hae-pohjavesialueurakat
    {}
    {:onnistui ->HaePohjavesialueurakatOnnistui
     :epaonnistui ->HaePohjavesialueurakatEpaonnistui
     :paasta-virhe-lapi? true}))

(extend-protocol tuck/Event

  ;; Haetaan urakat, joilla on olemassa pohjavesialueille tehtyjä rajoituksia
  HaePohjavesialueurakat
  (process-event [_ app]
    (let [_ (hae-pohjavesialueiden-urakat)]
      (assoc app :urakkahaku-kaynnissa? true)))

  HaePohjavesialueurakatOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :urakat vastaus)
      (assoc :urakkahaku-kaynnissa? false)))

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
        (update :urakat (fn [urakat]
                          (do
                            (mapv (fn [urakka]
                                    (if (= urakkaid (:id urakka))
                                      (assoc urakka :pohjavesialueet nil)
                                      urakka))
                              urakat))))
        (assoc :valittu-urakka urakkaid)
        (assoc :pohjavesialuehaku-kaynnissa? true))))

  HaeUrakanPohjavesialueetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [urakkaid (:valittu-urakka app)
          ;; Muokataan formiaattitiedot
          vastaus (map #(if (= 0 (:talvisuolaraja %))
                          (assoc % :formiaatti true)
                          (assoc % :formiaatti false))
                    vastaus)]
      (-> app
        (update :urakat (fn [urakat]
                          (do
                            (mapv (fn [urakka]
                                    (if (= urakkaid (:id urakka))
                                      (assoc urakka :pohjavesialueet vastaus)
                                      urakka))
                              urakat))))
        (assoc :pohjavesialuehaku-kaynnissa? false))))

  HaeUrakanPohjavesialueetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :pohjavesialuehaku-kaynnissa? false))

  TeeSiirto
  (process-event [{urakka :urakka} app]
    (let [pohjavesialueet (:pohjavesialueet urakka)
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
      (viesti/nayta-toast! "Pohjavesialuueet siirretty rajoitusalueiksi onnistueesti!" :onnistui)
      (hae-pohjavesialueiden-urakat) ;; Päivitetään vielä listaus, jotta jo siirretyt poistuvat
      (-> app
        (assoc :siirto-kaynnissa? false))))

  TeeSiirtoEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "TeeSiirtoEpaonnistui :: vastaus" (pr-str vastaus))
      (assoc app :siirto-kaynnissa? false)))

  )
