(ns harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]

            [harja.domain.vesivaylat.kiintio :as kiintio])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila (r/atom
                {:nakymassa? false
                      :kiintioiden-haku-kaynnissa? false
                       :kiintioiden-tallennus-kaynnissa? false
                      :kiintiot nil
                      :valinnat nil}))

(defonce valinnat
  (reaction
    (when (:nakymassa? tila)
      {:urakka-id (:id @nav/valittu-urakka)
       :sopimus-id (first @u/valittu-sopimusnumero)})))

(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [valinnat])
(defrecord HaeKiintiot [])
(defrecord KiintiotHaettu [tulos])
(defrecord KiintiotEiHaettu [])
(defrecord TallennaKiintiot [])
(defrecord KiintiotTallennettu [tulos])
(defrecord KiintiotEiTallennettu [])

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nak :nakymassa?} app]
    (assoc app :nakymassa? nak))

  PaivitaValinnat
  (process-event [{val :valinnat} app]
    (let [uudet-valinnat (merge (:valinnat app) val)
          haku (tuck/send-async! ->HaeKiintiot)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  HaeKiintiot
  (process-event [_ app]
    (if-not (:kiintioiden-haku-kaynnissa? app)
      (let [parametrit {::kiintio/urakka-id (get-in app [:valinnat :urakka-id])
                        ::kiintio/sopimus-id (get-in app [:valinnat :sopimus-id])}]
        (-> app
           (tuck-apurit/palvelukutsu :hae-kiintiot
                                     parametrit
                                     {:onnistui ->KiintiotHaettu
                               :epaonnistui ->KiintiotEiHaettu})
           (assoc :kiintioiden-haku-kaynnissa? true)))

      app))

  KiintiotHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :kiintioiden-haku-kaynnissa? false
               :kiintiot tulos))

  KiintiotEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kiintiöiden haku epäonnistui!" :danger)
    (assoc app :kiintioiden-haku-kaynnissa? false))

  TallennaKiintiot
  (process-event [_ app]
    (if-not (:kiintioiden-tallennus-kaynnissa? app)
      (let [parametrit {}]
        (-> app
            (tuck-apurit/palvelukutsu :tallenna-kiintiot
                                      parametrit
                                      {:onnistui ->KiintiotTallennettu
                                       :epaonnistui ->KiintiotEiTallennettu})
            (assoc :kiintioiden-tallennus-kaynnissa? true)))

      app))

  KiintiotTallennettu
  (process-event [{kiintiot :tulos} app]
    (assoc app :kiintiot kiintiot
               :kiintioiden-tallennus-kaynnissa? false))

  KiintiotEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Kiintiöiden tallennus epäonnistui!" :danger)
    (assoc app :kiintioiden-tallennus-kaynnissa? false)))

