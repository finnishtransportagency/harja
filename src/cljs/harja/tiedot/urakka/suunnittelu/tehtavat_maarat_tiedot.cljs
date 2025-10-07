(ns harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot
  (:require [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]))

(defonce nakymassa? (atom false))

(defrecord HaeTehtavatJaMaarat [parametrit])
(defrecord HaeTehtavatJaMaaratOnnistui [vastaus parametrit])
(defrecord HaeTehtavatJaMaaratEpaonnistui [vastaus parametrit])

(defrecord ToggleMuuttuneetTehtavat [])

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
    (js/console.log "HaeTehtavatJaMaaratOnnistui :: vastaus" (pr-str vastaus))
    (js/console.log "HaeTehtavatJaMaaratOnnistui :: parametrit" (pr-str parametrit))

    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tehtavat-ja-maarat vastaus)))

  HaeTehtavatJaMaaratEpaonnistui
  (process-event [{vastaus :vastaus parametrit :parametrit} app]
    (js/console.log "HaeTehtavatJaMaaratEpaonnistui")
    (-> app
      (assoc :haku-kaynnissa? false)))

  ToggleMuuttuneetTehtavat
  (process-event [_ app]
    (js/console.log "ToggleMuuttuneetTehtavat")
    (assoc app :nayta-muuttuneet-tehtavat (not (:nayta-muuttuneet-tehtavat app))))

    )
