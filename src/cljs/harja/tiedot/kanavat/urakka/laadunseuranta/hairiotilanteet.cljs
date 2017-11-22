(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.kanavat.hairiotilanne :as hairio]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.navigaatio :as navigaatio])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :hairiotilanteet nil
                 :hairiotilanteiden-haku-kaynnissa? false
                 :valinnat nil}))

;; Yleiset
(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [valinnat])
;; Haut
(defrecord HaeHairiotilanteet [valinnat])
(defrecord HairiotilanteetHaettu [tulos])
(defrecord HairiotilanteetEiHaettu [])
;; Muokkaukset
(defrecord LisaaHairiotilanne [])
(defrecord TyhjennaValittuHairiotilanne [])
(defrecord AsetaHairiotilanteenTiedot [hairiotilanne])
(defrecord TallennaHairiotilanne [hairiotilanne])

(def valinnat
  (reaction
    (when (:nakymassa? @tila)
      {:urakka @nav/valittu-urakka
       :sopimus-id (first @u/valittu-sopimusnumero)
       :aikavali @u/valittu-aikavali})))

(defn esitaytetty-hairiotilanne []
  (log "---->>> esitäytellään")
  {::hairio/sopimus-id (:paasopimus @navigaatio/valittu-urakka)})

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  PaivitaValinnat
  (process-event [{val :valinnat} app]
    (let [uudet-valinnat (merge (:valinnat app) val)
          haku (tuck/send-async! ->HaeHairiotilanteet)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  HaeHairiotilanteet
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:hairiotilanteiden-haku-kaynnissa? app))
             (some? (get-in valinnat [:urakka :id])))
      (let [argumentit {::hairio/urakka-id (get-in valinnat [:urakka :id])
                        :haku-sopimus-id (:sopimus-id valinnat)
                        :haku-vikaluokka (:vikaluokka valinnat)
                        :haku-korjauksen-tila (:korjauksen-tila valinnat)
                        :haku-odotusaika-h (:odotusaika-h valinnat)
                        :haku-korjausaika-h (:korjausaika-h valinnat)
                        :haku-paikallinen-kaytto? (:paikallinen-kaytto? valinnat)
                        :haku-aikavali (:aikavali valinnat)}]
        (-> app
            (tuck-apurit/post! :hae-hairiotilanteet
                               argumentit
                               {:onnistui ->HairiotilanteetHaettu
                                :epaonnistui ->HairiotilanteetEiHaettu})
            (assoc :hairiotilanteiden-haku-kaynnissa? true)))
      app))

  HairiotilanteetHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :hairiotilanteiden-haku-kaynnissa? false
               :hairiotilanteet tulos))

  HairiotilanteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Häiriötilanteiden haku epäonnistui!" :danger)
    (assoc app :hairiotilanteiden-haku-kaynnissa? false
               :hairiotilanteet []))

  LisaaHairiotilanne
  (process-event [_ app]
    (assoc app :valittu-hairiotilanne (esitaytetty-hairiotilanne)))

  TyhjennaValittuHairiotilanne
  (process-event [_ app]
    (dissoc app :valittu-hairiotilanne))

  AsetaHairiotilanteenTiedot
  (process-event [{hairiotilanne :hairiotilanne} app]
    (assoc app :valittu-hairiotilanne hairiotilanne))

  TallennaHairiotilanne
  (process-event [{hairiotilanne :hairiotilanne} {valinnat :valinnat :as app}]
    ;; todo
    app
    ))

