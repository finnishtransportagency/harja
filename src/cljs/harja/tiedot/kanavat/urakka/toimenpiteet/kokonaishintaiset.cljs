(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :as async]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.urakka :as urakka]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :valinnat nil
                 :haku-kaynnissa? false
                 :valitut-toimenpide-idt #{}
                 :toimenpiteet nil}))

(defonce valinnat
         (reaction
           (when (:nakymassa? @tila)
             {:urakka @nav/valittu-urakka
              :sopimus-id (first @u/valittu-sopimusnumero)
              :aikavali @u/valittu-aikavali
              :toimenpide @u/valittu-toimenpideinstanssi})))

;; Yleiset
(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [valinnat])
;; Haut
(defrecord HaeKokonaishintaisetToimenpiteet [valinnat])
(defrecord KokonaishintaisetToimenpiteetHaettu [toimenpiteet])
(defrecord KokonaishintaisetToimenpiteetEiHaettu [])
;; UI-toiminnot
(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseToimenpiteet [tiedot])

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
    (if (and
          (get-in valinnat [:urakka :id])
          (not (:haku-kaynnissa? app)))
      (let [argumentit (toimenpiteet/muodosta-hakuargumentit valinnat :kokonaishintainen)]
        (-> app
            (tuck-apurit/post! :hae-kanavatoimenpiteet
                               argumentit
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
               :toimenpiteet []))

  ValitseToimenpide
  (process-event [{tiedot :tiedot} app]
    (let [toimenpide-id (:id tiedot)
          valittu? (:valittu? tiedot)
          aseta-valinta (if valittu? conj disj)]
      (assoc app :valitut-toimenpide-idt
                 (aseta-valinta (:valitut-toimenpide-idt app) toimenpide-id))))

  ValitseToimenpiteet
  (process-event [{tiedot :tiedot} app]
    (let [kaikki-valittu? (:kaikki-valittu? tiedot)]
      (if kaikki-valittu?
        (assoc app :valitut-toimenpide-idt
                   (set (map ::kanavan-toimenpide/id (:toimenpiteet app))))
        (assoc app :valitut-toimenpide-idt #{})))))

