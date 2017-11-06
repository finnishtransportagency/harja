(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset
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
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavatoimenpide])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :valinnat {:urakka @nav/valittu-urakka}
                 :haku-kaynnissa? false
                 :toimenpiteet nil}))

(defonce valinnat
         (reaction
           (when (:nakymassa? @tila)
             {:urakka-id (:id @nav/valittu-urakka)
              :sopimus-id (first @u/valittu-sopimusnumero)
              :aikavali @u/valittu-aikavali
              :toimenpide @u/valittu-toimenpideinstanssi})))

(defn muodosta-hakuparametrit [valinnat]
  {::sopimus/id (:sopimus-id valinnat)
   ::toimenpidekoodi/id (get-in valinnat [:toimenpide :id])
   ::kanavatoimenpide/alkupvm (first (:aikavali valinnat))
   ::kanavatoimenpide/loppupvm (second (:aikavali valinnat))
   ::kanavatoimenpide/kanava-toimenpidetyyppi :kokonaishintainen})

(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [valinnat])
(defrecord HaeKokonaishintaisetToimenpiteet [valinnat])
(defrecord KokonaishintaisetToimenpiteetHaettu [toimenpiteet])
(defrecord KokonaishintaisetToimenpiteetEiHaettu [])

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  PaivitaValinnat
  (process-event [{valinnat :valinnat} app]
    (let [haku (tuck/send-async! ->HaeKokonaishintaisetToimenpiteet)]
      (go (haku valinnat))
      (assoc app :valinnat valinnat)))

  HaeKokonaishintaisetToimenpiteet
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:haku-kaynnissa? app)))
      (let [parametrit (muodosta-hakuparametrit valinnat)]
        (-> app
            (tuck-apurit/post! :hae-kanavatoimenpiteet
                               parametrit
                               {:onnistui ->KokonaishintaisetToimenpiteetHaettu
                                :epaonnistui ->KokonaishintaisetToimenpiteetEiHaettu})
            (assoc :haku-kaynnissa? true)))
      app))

  KokonaishintaisetToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (assoc app :haku-kaynnissa? false
               :toimenpiteet toimenpiteet))

  KokonaishintaisetToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kokonaishintaisten toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :haku-kaynnissa? false
               :toimenpiteet [])))

