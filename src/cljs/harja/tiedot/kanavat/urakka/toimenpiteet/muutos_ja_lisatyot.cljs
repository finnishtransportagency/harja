(ns harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :as async]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as u]
            [harja.domain.urakka :as urakka]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :toimenpiteiden-siirto-kaynnissa? false
                 :valitut-toimenpide-idt #{}
                 :toimenpiteet nil
                 :toimenpiteiden-haku-kaynnissa? false}))

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
(defrecord HaeToimenpiteet [valinnat])
(defrecord ToimenpiteetHaettu [tulos])
(defrecord ToimenpiteetEiHaettu [])
;; UI-toiminnot
(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseToimenpiteet [tiedot])
(defrecord SiirraValitut [])
(defrecord ValitutSiirretty [])
(defrecord ValitutEiSiirretty [])

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  PaivitaValinnat
  (process-event [{valinnat :valinnat} app]
    (let [haku (tuck/send-async! ->HaeToimenpiteet)]
      (go (haku valinnat))
      (assoc app :valinnat valinnat)))


  HaeToimenpiteet
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:toimenpiteiden-haku-kaynnissa? app))
             (get-in valinnat [:urakka :id]))
      (let [argumentit (toimenpiteet/muodosta-hakuargumentit valinnat :muutos-lisatyo)]
        (-> app
            (tuck-apurit/post! :hae-kanavatoimenpiteet
                               argumentit
                               {:onnistui ->ToimenpiteetHaettu
                                :epaonnistui ->ToimenpiteetEiHaettu})
            (assoc :toimenpiteiden-haku-kaynnissa? true)))
      app))

  ToimenpiteetHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :toimenpiteiden-haku-kaynnissa? false
               :toimenpiteet tulos))

  ToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :toimenpiteiden-haku-kaynnissa? false
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
        (assoc app :valitut-toimenpide-idt #{}))))

  SiirraValitut
  (process-event [_ app]
    (when-not (:toimenpiteioden-siirto-kaynnissa? app)
      (-> app
          (tuck-apurit/post! :siirra-kanavatoimenpiteet
                             {::kanavan-toimenpide/toimenpide-idt (:valitut-toimenpide-idt app)
                              ::kanavan-toimenpide/urakka-id (get-in app [:valinnat :urakka :id])
                              ::kanavan-toimenpide/tyyppi :kokonaishintainen}
                             {:onnistui ->ValitutSiirretty
                              :epaonnistui ->ValitutEiSiirretty})
          (assoc :toimenpiteioden-siirto-kaynnissa? true))))

  ValitutSiirretty
  (process-event [_ app]
    (viesti/nayta! (toimenpiteet-view/toimenpiteiden-toiminto-suoritettu
                     (count (:valitut-toimenpide-idt app)) "siirretty") :success)
    (assoc app :toimenpiteiden-siirto-kaynnissa? false
               :valitut-toimenpide-idt #{}
               :toimenpiteet (filter
                               (fn [toimenpide]
                                 (not ((:valitut-toimenpide-idt app)
                                        (::kanavan-toimenpide/id toimenpide))))
                               (:toimenpiteet app))))

  ValitutEiSiirretty
  (process-event [_ app]
    (viesti/nayta! "Siiro epäonnistui" :danger)
    (assoc app :toimenpiteiden-siirto-kaynnissa? false)))