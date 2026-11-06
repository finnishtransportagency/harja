(ns harja.tiedot.hallinta.urakkatiedot.vesivayla-tehtava-tiedot
  "Vesiväylä-hoidon tehtäväryhmien ja tehtävien hallinnan tila."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom {:valitut-tehtavat {}
                 :tulostetut-tehtavat []}))
(def nakymassa? (atom false))

(defrecord HaeTehtavaryhmaotsikot [])
(defrecord HaeTehtavaryhmaotsikotOnnistui [vastaus])
(defrecord HaeTehtavaryhmaotsikotEpaonnistui [vastaus])
(defrecord MuokkaaTehtavaryhmat [rivit])
(defrecord MuokkaaTehtavaryhmatOnnistui [vastaus])
(defrecord MuokkaaTehtavaryhmatEpaonnistui [vastaus])
(defrecord MuokkaaTehtavat [rivit])
(defrecord MuokkaaTehtavatOnnistui [vastaus])
(defrecord MuokkaaTehtavatEpaonnistui [vastaus])
(defrecord ValitseTehtava [tehtava valittu?])
(defrecord TyhjaaValitutTehtavat [])
(defrecord TulostaKaikkiValitut [])

(defn tehtava-valittu? [tehtava-id]
  (contains? (:valitut-tehtavat @tila) tehtava-id))

(extend-protocol tuck/Event
  ValitseTehtava
  (process-event [{:keys [tehtava valittu?]} app]
    (let [tehtava-id (:id tehtava)]
      (assoc (if valittu?
               (assoc-in app [:valitut-tehtavat tehtava-id] tehtava)
               (update app :valitut-tehtavat dissoc tehtava-id))
        :tulostetut-tehtavat [])))

  TulostaKaikkiValitut
  (process-event [_ app]
    (assoc app :tulostetut-tehtavat (vals (:valitut-tehtavat app))))

  TyhjaaValitutTehtavat
  (process-event [_ app]
    (assoc app
      :valitut-tehtavat {}
      :tulostetut-tehtavat []))

  HaeTehtavaryhmaotsikot
  (process-event [_ app]
    (tuck-apurit/post! :hae-vvh-tehtavaryhmaotsikot
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
      (js/console.error "HaeVvhTehtavaryhmaotsikotEpaonnistui :: error:" (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil)))

  MuokkaaTehtavaryhmat
  (process-event [{rivit :rivit} app]
    (tuck-apurit/post! :hallinta-tallenna-vvh-tehtavaryhmat
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
      (js/console.error "MuokkaaVvhTehtavaryhmatEpaonnistui :: error:" (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil)))

  MuokkaaTehtavat
  (process-event [{rivit :rivit} app]
    (tuck-apurit/post! :hallinta-tallenna-vvh-tehtavat
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
      (js/console.error "MuokkaaVvhTehtavatEpaonnistui :: error:" (pr-str vastaus))
      (assoc app :tehtavaryhmaotsikot nil))))