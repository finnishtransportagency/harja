(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log error]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.urakka :as ur]
            [cljs.core.async :as async :refer [<!]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [cljs.spec.alpha :as s]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.tyokalut.tuck :as tuck-tyokalut]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila
  (atom {:valinnat {:urakka-id nil
                    :sopimus-id nil
                    :aikavali [nil nil]
                    :vaylatyyppi nil
                    :vayla nil
                    :tyolaji nil
                    :tyoluokka nil
                    :toimenpide nil
                    :vain-vikailmoitukset? false}
         :nakymassa? false
         :toimenpiteiden-haku-kaynnissa? false
         :kiintioiden-haku-kaynnissa? false
         :infolaatikko-nakyvissa {} ; tunniste -> boolean
         :valittu-kiintio-id nil
         :kiintioon-liittaminen-kaynnissa? false
         :liitteen-lisays-kaynnissa? false
         :liitteen-poisto-kaynnissa? false
         :toimenpiteet nil
         :turvalaitteet-kartalla nil
         :karttataso-nakyvissa? false
         :korostetut-turvalaitteet nil
         :korostettu-kiintio false
         :avoimet-kiintiot #{}}))

(defonce karttataso-kokonaishintaisten-turvalaitteet (r/cursor tila [:karttataso-nakyvissa?]))
(defonce turvalaitteet-kartalla (r/cursor tila [:turvalaitteet-kartalla]))

(def valinnat
  (reaction
    (when (:nakymassa? @tila)
      {:urakka-id (:id @nav/valittu-urakka)
       :sopimus-id (first @u/valittu-sopimusnumero)
       :aikavali @u/valittu-aikavali})))

(def vaylahaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [vastaus (<! (k/post! :hae-vaylat {:hakuteksti teksti
                                                  :vaylatyyppi (get-in @tila [:valinnat :vaylatyyppi])}))]
            vastaus)))))

(def turvalaitehaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [vastaus (<! (k/post! :hae-turvalaitteet-tekstilla {:hakuteksti teksti}))]
            vastaus)))))

(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [tiedot])
(defrecord HaeToimenpiteet [valinnat])
(defrecord ToimenpiteetHaettu [toimenpiteet])
(defrecord ToimenpiteetEiHaettu [virhe])
(defrecord HaeKiintiot [])
(defrecord KiintiotHaettu [kiintiot])
(defrecord KiintiotEiHaettu [virhe])
(defrecord ValitseKiintio [kiintio-id])
(defrecord SiirraValitutYksikkohintaisiin [])
(defrecord LiitaToimenpiteetKiintioon [])
(defrecord ToimenpiteetLiitettyKiintioon [vastaus])
(defrecord ToimenpiteetEiLiitettyKiintioon [])
(defrecord AvaaKiintio [id])
(defrecord SuljeKiintio [id])
;; Kartta
(defrecord KorostaKiintioKartalla [kiintio])
(defrecord PoistaKiintionKorostus [])

(def valiaikainen-kiintio
  {::kiintio/nimi "Kiintiöttömät"
   ::kiintio/id -1})

(defn kiintiottomat-toimenpiteet-valiaikaisiin-kiintioihin [toimenpiteet]
  (for [to toimenpiteet]
    (assoc to ::to/kiintio (or (::to/kiintio to)
                               valiaikainen-kiintio))))

(defn kiintio-korostettu? [kiintio {:keys [korostettu-kiintio]}]
  (boolean
    (when-not (false? korostettu-kiintio)
      (= (::kiintio/id kiintio) korostettu-kiintio))))

(defn poista-kiintion-korostus [app]
  (assoc app :korostettu-kiintio false))

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?
               :karttataso-nakyvissa? nakymassa?))

  PaivitaValinnat
  ;; Valintojen päivittäminen laukaisee aina myös kantahaun uusimmilla valinnoilla (ellei ole jo käynnissä),
  ;; jotta näkymä pysyy synkassa valintojen kanssa
  (process-event [{tiedot :tiedot} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys tiedot jaettu/valintojen-avaimet))
          haku (tuck/send-async! ->HaeToimenpiteet)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  SiirraValitutYksikkohintaisiin
  (process-event [_ app]
    (jaettu/siirra-valitut! :siirra-toimenpiteet-yksikkohintaisiin app))

  HaeToimenpiteet
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:toimenpiteiden-haku-kaynnissa? app))
             (some? (:urakka-id valinnat)))
      (-> app
          (tuck-tyokalut/post! :hae-kokonaishintaiset-toimenpiteet
                               (jaettu/toimenpiteiden-hakukyselyn-argumentit valinnat)
                               {:onnistui ->ToimenpiteetHaettu
                                       :epaonnistui ->ToimenpiteetEiHaettu})
          (assoc :toimenpiteiden-haku-kaynnissa? true))
      app))

  ToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (let [turvalaitteet-kartalle (tuck/send-async! jaettu/->HaeToimenpiteidenTurvalaitteetKartalle)]
      (go (turvalaitteet-kartalle toimenpiteet))
      (assoc app :toimenpiteet (-> toimenpiteet
                                   jaettu/korosta-harjassa-luodut
                                   kiintiottomat-toimenpiteet-valiaikaisiin-kiintioihin
                                   jaettu/toimenpiteet-aikajarjestyksessa)
                 :toimenpiteiden-haku-kaynnissa? false)))

  ToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :toimenpiteiden-haku-kaynnissa? false))

  HaeKiintiot
  (process-event [_ app]
    (if-not (:kiintioiden-haku-kaynnissa? app)
      (-> app
          (tuck-tyokalut/post! :hae-kiintiot
                               {::kiintio/urakka-id (get-in app [:valinnat :urakka-id])
                                       ::kiintio/sopimus-id (get-in app [:valinnat :sopimus-id])}
                               {:onnistui ->KiintiotHaettu
                                       :epaonnistui ->KiintiotEiHaettu})
          (assoc :kiintioiden-haku-kaynnissa? true))
      app))

  KiintiotHaettu
  (process-event [{kiintiot :kiintiot} app]
    (assoc app :kiintiot kiintiot
               :kiintioiden-haku-kaynnissa? false))

  KiintiotEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kiintiöiden haku epäonnistui!" :danger)
    (assoc app :kiintioiden-haku-kaynnissa? false))

  ValitseKiintio
  (process-event [{kiintio-id :kiintio-id} app]
    (assoc app :valittu-kiintio-id kiintio-id))

  LiitaToimenpiteetKiintioon
  (process-event [_ app]
    (if-not (:kiintioon-liittaminen-kaynnissa? app)
      (-> app
          (tuck-tyokalut/post! :liita-toimenpiteet-kiintioon
                               {::kiintio/id (:valittu-kiintio-id app)
                                       ::kiintio/urakka-id (get-in app [:valinnat :urakka-id])
                                       ::to/idt (map ::to/id (jaettu/valitut-toimenpiteet (:toimenpiteet app)))}
                               {:onnistui ->ToimenpiteetLiitettyKiintioon
                                       :epaonnistui ->ToimenpiteetEiLiitettyKiintioon})
          (assoc :kiintioon-liittaminen-kaynnissa? true))
      app))

  ToimenpiteetLiitettyKiintioon
  (process-event [{vastaus :vastaus} app]
    (let [toimenpidehaku (tuck/send-async! ->HaeToimenpiteet)]
      (viesti/nayta! (jaettu/toimenpiteiden-toiminto-suoritettu (count (::to/idt vastaus)) "liitetty") :success)
      (go (toimenpidehaku (:valinnat app)))
      (assoc app :kiintioon-liittaminen-kaynnissa? false
                 :valittu-kiintio-id nil)))

  ToimenpiteetEiLiitettyKiintioon
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden liittäminen kiintiöön epäonnistui!" :danger)
    (assoc app :kiintioon-liittaminen-kaynnissa? false))

  AvaaKiintio
  (process-event [{id :id} app]
    (if (nil? (:avoimet-kiintiot app))
      (assoc app :avoimet-kiintiot #{id})
      (update app :avoimet-kiintiot conj id)))

  SuljeKiintio
  (process-event [{id :id} app]
    (if (nil? (:avoimet-kiintiot app))
      app
      (update app :avoimet-kiintiot disj id)))

  KorostaKiintioKartalla
  (process-event [{kiintio :kiintio} {:keys [toimenpiteet] :as app}]
    (let [korostettavat-turvalaitteet (->>
                                        toimenpiteet
                                        (filter #(= (get-in % [::to/kiintio ::kiintio/id]) (::kiintio/id kiintio)))
                                        (map (comp ::tu/turvalaitenro ::to/turvalaite))
                                        (into #{}))]
      (-> (jaettu/korosta-kartalla korostettavat-turvalaitteet app)
          (assoc :korostettu-kiintio (::kiintio/id kiintio)))))

  PoistaKiintionKorostus
  (process-event [_ app]
    (->> app
         (poista-kiintion-korostus)
         (jaettu/korosta-kartalla nil))))
