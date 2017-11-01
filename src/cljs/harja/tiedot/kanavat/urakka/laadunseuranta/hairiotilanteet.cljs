(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :as async]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tyokalut.tuck :as tuck-apurit])
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
(defrecord HaeHairioTilanteet [valinnat])
(defrecord HairioTilanteetHaettu [tulos])
(defrecord HairioTilanteetEiHaettu [])

(defonce valinnat
  (reaction
    (when (:nakymassa? @tila)
      {:urakka-id (:id @nav/valittu-urakka)
       :sopimus-id (first @u/valittu-sopimusnumero)})))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  PaivitaValinnat
  (process-event [{val :valinnat} app]
    (let [uudet-valinnat (merge (:valinnat app) val)
          ;haku (tuck/send-async! ->HaeKiintiot)
          ]
      #_(go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  HaeHairioTilanteet
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:hairiotilanteiden-haku-kaynnissa? app))
             (some? (:urakka-id valinnat)))
      (let [parametrit {::hairiotilanne/urakka-id (:urakka-id valinnat)
                        ::hairiotilanne/sopimus-id (:sopimus-id valinnat)}]
        (-> app
            (tuck-apurit/post! :hae-hairiotilanteet
                               parametrit
                               {:onnistui ->HairioTilanteetHaettu
                                :epaonnistui ->HairioTilanteetEiHaettu})
            (assoc :hairiotilanteiden-haku-kaynnissa? true)))

      app))

  HairioTilanteetHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :hairiotilanteiden-haku-kaynnissa? false
               :hairiotilanteet tulos))

  HairioTilanteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Häiriötilanteiden haku epäonnistui!" :danger)
    (assoc app :hairiotilanteiden-haku-kaynnissa? false
               :hairiotilanteet [])))

