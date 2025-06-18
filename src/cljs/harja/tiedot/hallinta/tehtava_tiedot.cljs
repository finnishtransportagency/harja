(ns harja.tiedot.hallinta.tehtava-tiedot
  "Tehtävien ja tehtäväryhmien ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.yleiset :as yleiset]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom nil))
(def nakymassa? (atom false))

;; Valittujen tehtävien tila: Map, jossa avaimena on tehtävän id ja arvona tehtävän tiedot
(def valitut-tehtavat (atom {}))

;; Tallennetaan kaikki tulostetut tehtävät
(def tulostetut-tehtavat (atom []))

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
  (contains? @valitut-tehtavat tehtava-id))


(defn tulosta-kaikki-valitut-tehtavat []
  (let [valitut (vals @valitut-tehtavat)] 
    (reset! tulostetut-tehtavat valitut)
    valitut))

(extend-protocol tuck/Event
  ValitseTehtava
  (process-event [{:keys [tehtava valittu?]} app]
    (let [tehtava-id (:id tehtava)]
      (if valittu?
        ;; Lisää tehtävä valittuihin
        (swap! valitut-tehtavat assoc tehtava-id tehtava)
        ;; Poista tehtävä valituista
        (swap! valitut-tehtavat dissoc tehtava-id))
      app)) 
  
  TulostaKaikkiValitut
  (process-event [_ app]
    (tulosta-kaikki-valitut-tehtavat)
    app)
  
  TyhjaaValitutTehtavat
  (process-event [_ app]
    (reset! valitut-tehtavat {})
    app)

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