(ns harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.id :refer [id-olemassa?]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [>! <!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.tiedot.urakka :as u]

            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as to-jaettu]
            [harja.domain.muokkaustiedot :as m]
            [clojure.set :as set])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila (r/atom
                {:nakymassa? false
                 :kiintioiden-haku-kaynnissa? false
                 :kiintioiden-tallennus-kaynnissa? false
                 :kiintiosta-irrotus-kaynnissa? false
                 :kiintiot nil
                 :valitut-toimenpide-idt #{}
                 :valinnat nil}))

(defonce valinnat
  (reaction
    (when (:nakymassa? @tila)
      {:urakka-id (:id @nav/valittu-urakka)
       :sopimus-id (first @u/valittu-sopimusnumero)})))

(defn- kiintiot-ilman-toimenpiteita [kiintiot irrotettavat-toimenpide-idt]
  (fmap (fn [kiintio]
          (assoc kiintio ::kiintio/toimenpiteet (to/ilman-toimenpiteita
                                                  (::kiintio/toimenpiteet kiintio)
                                                  irrotettavat-toimenpide-idt)))
        kiintiot))

;; Yleiset
(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [valinnat])
;; Haut
(defrecord HaeKiintiot [valinnat])
(defrecord KiintiotHaettu [tulos])
(defrecord KiintiotEiHaettu [])
;; Toiminnot
(defrecord TallennaKiintiot [grid paluukanava])
(defrecord IrrotaKiintiosta [toimenpide-idt])
(defrecord KiintiotTallennettu [tulos paluukanava])
(defrecord KiintiotEiTallennettu [virhe paluukanava])
(defrecord IrrotettuKiintiosta [vastaus])
(defrecord EiIrrotettuKiintiosta [])
(defrecord ValitseToimenpide [tiedot])

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
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:kiintioiden-haku-kaynnissa? app))
             (some? (:urakka-id valinnat)))
      (let [parametrit {::kiintio/urakka-id (:urakka-id valinnat)
                        ::kiintio/sopimus-id (:sopimus-id valinnat)}]
        (-> app
            (tuck-apurit/post! :hae-kiintiot-ja-toimenpiteet
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
                        ;; Muutetaan gridin sisäinen :poistettu avain oikeaan muotoon
                        (map
                          (fn [k]
                            (set/rename-keys k {:poistettu ::m/poistettu?}))
                          kiintiot)}]
        (-> app
            (tuck-apurit/post! :tallenna-kiintiot
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
    (assoc app :kiintioiden-tallennus-kaynnissa? false))

  ValitseToimenpide
  (process-event [{tiedot :tiedot} app]
    (let [toimenpide-id (:id tiedot)
          valittu? (:valittu? tiedot)
          aseta-valinta (if valittu? conj disj)]
      (assoc app :valitut-toimenpide-idt
                 (aseta-valinta (:valitut-toimenpide-idt app) toimenpide-id))))

  IrrotaKiintiosta
  (process-event [{toimenpide-idt :toimenpide-idt} app]
    (if-not (:kiintiosta-irrotus-kaynnissa? app)
      (let [parametrit {::to/urakka-id (get-in app [:valinnat :urakka-id])
                        ::to/idt toimenpide-idt}]
        (-> app
            (tuck-apurit/post! :irrota-toimenpiteet-kiintiosta
                               parametrit
                               {:onnistui ->IrrotettuKiintiosta
                                :epaonnistui ->EiIrrotettuKiintiosta})
            (assoc :kiintiosta-irrotus-kaynnissa? true)))
      app))

  IrrotettuKiintiosta
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! (to-jaettu/toimenpiteiden-toiminto-suoritettu (count (::to/idt vastaus))
                                                                 "irrotettu kiintiöstä")
                   :success)
    (assoc app :valitut-toimenpide-idt #{}
               :kiintiosta-irrotus-kaynnissa? false
               :kiintiot (kiintiot-ilman-toimenpiteita (:kiintiot app)
                                                       (::to/idt vastaus))))

  EiIrrotettuKiintiosta
  (process-event [_ app]
    (viesti/nayta! "Irrotus epäonnistui!" :danger)
    (assoc app :kiintiosta-irrotus-kaynnissa? false)))
