(ns harja.tiedot.hallinta.tehtava-tiedot
  "Tehtävien ja tehtäväryhmien ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.yleiset :as yleiset]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom {:valitut-tehtavat {}
                 :tulostetut-tehtavat []}))
(def nakymassa? (atom false))

(defrecord HaeTehtavaryhmaotsikot [])
(defrecord HaeTehtavaryhmaotsikotOnnistui [vastaus])
(defrecord HaeTehtavaryhmaotsikotEpaonnistui [vastaus])
(defrecord HaeSuoritettavatTehtavat [])
(defrecord HaeSuoritettavatTehtavatOnnistui [vastaus])
(defrecord HaeSuoritettavatTehtavatEpaonnistui [vastaus])
(defrecord MuokkaaTehtavaryhmat [rivit])
(defrecord MuokkaaTehtavaryhmatOnnistui [vastaus])
(defrecord MuokkaaTehtavaryhmatEpaonnistui [vastaus])
(defrecord MuokkaaTehtavat [rivit])
(defrecord MuokkaaTehtavatOnnistui [vastaus])
(defrecord MuokkaaTehtavatEpaonnistui [vastaus])

(defrecord ValitseTehtava [tehtava valittu?])
(defrecord TyhjaaValitutTehtavat [])
(defrecord TulostaKaikkiValitut [])

(defn tehtava-valittu?
  "Tarkistaa, onko annettu tehtävä valittujen tehtävien joukossa."
  [tehtava-id]
  (contains? (:valitut-tehtavat @tila) tehtava-id))

(extend-protocol tuck/Event
  ValitseTehtava
  (process-event [{:keys [tehtava valittu?]} app]
    (let [tehtava-id (:id tehtava)]
      (if valittu?
        ;; Lisää tehtävä valittuihin
        (assoc-in app [:valitut-tehtavat tehtava-id] tehtava)
        ;; Poista tehtävä valituista
        (update app :valitut-tehtavat dissoc tehtava-id)))) 
  
  TulostaKaikkiValitut
  (process-event [_ app]
    (let [valitut (vals (:valitut-tehtavat app))]
      (assoc app :tulostetut-tehtavat valitut)))
  
  TyhjaaValitutTehtavat
  (process-event [_ app]
    (assoc app :valitut-tehtavat {}))

  HaeTehtavaryhmaotsikot
  (process-event [_ app]
    (tuck-apurit/post! :hae-mhu-tehtavaryhmaotsikot
      {}
      {:onnistui ->HaeTehtavaryhmaotsikotOnnistui
       :epaonnistui ->HaeTehtavaryhmaotsikotEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeTehtavaryhmaotsikotOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tehtavaryhmaotsikot vastaus))

  HaeTehtavaryhmaotsikotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaeTehtavaryhmaotsikotEpaonnistui :: error:" (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil)))

  HaeSuoritettavatTehtavat
  (process-event [_ app]
    (tuck-apurit/post! :hae-suoritettavat-tehtavat
      {}
      {:onnistui ->HaeSuoritettavatTehtavatOnnistui
       :epaonnistui ->HaeSuoritettavatTehtavatEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeSuoritettavatTehtavatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :suoritettavat-tehtavat vastaus))

  HaeSuoritettavatTehtavatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.error "HaeSuoritettavatTehtavatEpaonnistui :: error:" (pr-str vastaus))
      (assoc app :suoritettavat-tehtavat nil)))

  MuokkaaTehtavaryhmat
  (process-event [{rivit :rivit} app]
    (tuck-apurit/post! :hallinta-tallenna-tehtavaryhmat
      {:muokatut-tehtavaryhmat rivit}
      {:onnistui ->MuokkaaTehtavaryhmatOnnistui
       :epaonnistui ->MuokkaaTehtavaryhmatEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  MuokkaaTehtavaryhmatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tehtavaryhmaotsikot vastaus))

  MuokkaaTehtavaryhmatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.error "MuokkaaTehtavaryhmatEpaonnistui :: error: " (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil)))

  MuokkaaTehtavat
  (process-event [{rivit :rivit} app]
    (tuck-apurit/post! :hallinta-tallenna-tehtavat
      {:muokatut-tehtavat rivit}
      {:onnistui ->MuokkaaTehtavatOnnistui
       :epaonnistui ->MuokkaaTehtavatEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  MuokkaaTehtavatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tehtavaryhmaotsikot vastaus))

  MuokkaaTehtavatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.error "MuokkaaTehtavatEpaonnistui :: error: " (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil))))