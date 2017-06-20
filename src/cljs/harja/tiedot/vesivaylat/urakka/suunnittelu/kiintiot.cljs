(ns harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [>! <!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]

            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.muokkaustiedot :as m]
            [clojure.set :as set])
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
    (when (:nakymassa? @tila)
      {:urakka-id (:id @nav/valittu-urakka)
       :sopimus-id (first @u/valittu-sopimusnumero)})))

(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [valinnat])
(defrecord HaeKiintiot [])
(defrecord KiintiotHaettu [tulos])
(defrecord KiintiotEiHaettu [])
(defrecord TallennaKiintiot [grid paluukanava])
(defrecord KiintiotTallennettu [tulos paluukanava])
(defrecord KiintiotEiTallennettu [virhe paluukanava])

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
    (assoc app :kiintioiden-haku-kaynnissa? false
               :kiintiot []))

  TallennaKiintiot
  (process-event [{kiintiot :grid ch :paluukanava} app]
    (if-not (:kiintioiden-tallennus-kaynnissa? app)
      (let [parametrit {::kiintio/urakka-id (get-in app [:valinnat :urakka-id])
                        ::kiintio/sopimus-id (get-in app [:valinnat :sopimus-id])
                        ::kiintio/tallennettavat-kiintiot
                        (map
                          (fn [k]
                            (set/rename-keys k {:poistettu ::m/poistettu?}))
                          kiintiot)}]
        (-> app
            (tuck-apurit/palvelukutsu :tallenna-kiintiot
                                      parametrit
                                      {:onnistui ->KiintiotTallennettu
                                       :onnistui-parametrit [ch]
                                       :epaonnistui ->KiintiotEiTallennettu
                                       :epaonnistui-parametrit [ch]})
            (assoc :kiintioiden-tallennus-kaynnissa? true)))

      app))

  KiintiotTallennettu
  (process-event [{kiintiot :tulos ch :paluukanava} app]
    (go (>! ch kiintiot))
    (assoc app :kiintiot kiintiot
               :kiintioiden-tallennus-kaynnissa? false))

  KiintiotEiTallennettu
  (process-event [{ch :paluukanava} app]
    (viesti/nayta! "Kiintiöiden tallennus epäonnistui!" :danger)
    (go (>! ch (:kiintiot app)))
    (assoc app :kiintioiden-tallennus-kaynnissa? false)))