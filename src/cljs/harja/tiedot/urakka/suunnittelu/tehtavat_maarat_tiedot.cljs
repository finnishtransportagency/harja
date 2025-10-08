(ns harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot
  (:require [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]))

(defonce nakymassa? (atom false))

;; Muutosten seuranta
(defonce tallentamattomia-muutoksia (atom false))

(defn merkitse-muutos!
  "Merkitsee että muutoksia on tehty"
  []
  (reset! tallentamattomia-muutoksia true))

(defn nollaa-muutokset!
  "Nollaa muutosten seurannan"
  []
  (reset! tallentamattomia-muutoksia false))

(defn onko-muutoksia?
  "Tarkistaa onko tallentamattomia muutoksia"
  []
  @tallentamattomia-muutoksia)

(defrecord HaeTehtavatJaMaarat [parametrit])
(defrecord HaeTehtavatJaMaaratOnnistui [vastaus parametrit])
(defrecord HaeTehtavatJaMaaratEpaonnistui [vastaus parametrit])

(defrecord TallennaTehtavat [tehtavat])
(defrecord TallennaTehtavatOnnistui [vastaus])
(defrecord TallennaTehtavatEpaonnistui [vastaus])

(defrecord ToggleTallennus [])
(defrecord PeruutaTallennus [])
(defrecord PaivitaTehtavatGrid [tehtavat])

(extend-protocol tuck/Event

  HaeTehtavatJaMaarat
  (process-event [{parametrit :parametrit} app]
    (js/console.log "HaeTehtavatJaMaarat :: parametrit " (pr-str parametrit))

    (tuck-apurit/post! :hae-tehtavat-ja-maarat
      {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
       :hoitokauden-alkuvuosi :kaikki}
      {:onnistui ->HaeTehtavatJaMaaratOnnistui
       :epaonnistui ->HaeTehtavatJaMaaratEpaonnistui
       :onnistui-parametrit [parametrit]
       :paasta-virhe-lapi? true})
    (assoc app :haku-kaynnissa? true))

  HaeTehtavatJaMaaratOnnistui
  (process-event [{vastaus :vastaus parametrit :parametrit} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tehtavat-ja-maarat (:tehtavat vastaus))
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
      ))

  HaeTehtavatJaMaaratEpaonnistui
  (process-event [{vastaus :vastaus parametrit :parametrit} app]
    (js/console.log "HaeTehtavatJaMaaratEpaonnistui")
    (-> app
      (assoc :haku-kaynnissa? false)))

  TallennaTehtavat
  (process-event [{tehtavat :tehtavat} app]
    (tuck-apurit/post! :tallenna-tehtavat-ja-maarat
      {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
       :tehtavat tehtavat}
      {:onnistui ->TallennaTehtavatOnnistui
       :epaonnistui ->TallennaTehtavatEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kaynnissa? true))

  TallennaTehtavatOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :tallennus-kaynnissa? false)
      (assoc :tehtavat-ja-maarat (:tehtavat vastaus))
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))))

  TallennaTehtavatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :tallennus-kaynnissa? false)))

  PaivitaTehtavatGrid
  (process-event [{tehtavat :tehtavat} app]
    (js/console.log "PaivitaTehtavatGrid")
    (merkitse-muutos!)
    (assoc app :tehtavat-ja-maarat (sort-by :jarjestys tehtavat)))

  ToggleTallennus
  (process-event [_ app]
    (js/console.log "ToggleTallennus")
    (assoc app :tallennustila? (not (:tallennustila? app))))

  PeruutaTallennus
  (process-event [_ app]
    (js/console.log "ToggleTallennus")

    (tuck-apurit/post! :hae-tehtavat-ja-maarat
      {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
       :hoitokauden-alkuvuosi :kaikki}
      {:onnistui ->HaeTehtavatJaMaaratOnnistui
       :epaonnistui ->HaeTehtavatJaMaaratEpaonnistui
       :paasta-virhe-lapi? true})

    (assoc app :tallennustila? (not (:tallennustila? app))))

  )
