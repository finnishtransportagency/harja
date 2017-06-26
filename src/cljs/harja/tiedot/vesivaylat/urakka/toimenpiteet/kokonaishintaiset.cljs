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
            [harja.tyokalut.tuck :as tuck-tyokalut])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila
  (atom {:valinnat {:urakka-id nil
                    :sopimus-id nil
                    :aikavali [nil nil]
                    :vaylatyyppi :kauppamerenkulku
                    :vayla nil
                    :tyolaji nil
                    :tyoluokka nil
                    :toimenpide nil
                    :vain-vikailmoitukset? false}
         :nakymassa? false
         :toimenpiteiden-haku-kaynnissa? false
         :kiintioiden-haku-kaynnissa? false
         :liita-kiintioon nil ;; kiintiö-id
         :infolaatikko-nakyvissa {} ; tunniste -> boolean
         :kiintioon-liittaminen-kaynnissa? false
         :toimenpiteet nil}))

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

(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [tiedot])
(defrecord HaeToimenpiteet [valinnat])
(defrecord ToimenpiteetHaettu [toimenpiteet])
(defrecord ToimenpiteetEiHaettu [virhe])
(defrecord HaeKiintiot [])
(defrecord KiintiotHaettu [kiintiot])
(defrecord KiintiotEiHaettu [virhe])
(defrecord SiirraValitutYksikkohintaisiin [])
(defrecord LiitaToimenpiteetKiintioon [toimenpide-idt])
(defrecord ToimenpiteetLiitettyKiintioon [])
(defrecord ToimenpiteetEiLiitettyKiintioon [])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

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
    (if-not (:toimenpiteiden-haku-kaynnissa? app)
      (-> app
          (tuck-tyokalut/palvelukutsu :hae-kokonaishintaiset-toimenpiteet
                                      (jaettu/toimenpiteiden-hakukyselyn-argumentit valinnat)
                                      {:onnistui ->ToimenpiteetHaettu
                                       :epaonnistui ->ToimenpiteetEiHaettu})
          (assoc :toimenpiteiden-haku-kaynnissa? true))
      app))

  ToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (assoc app :toimenpiteet (jaettu/toimenpiteet-aikajarjestyksessa toimenpiteet)
               :toimenpiteiden-haku-kaynnissa? false))

  ToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :toimenpiteiden-haku-kaynnissa? false))

  HaeKiintiot
  (process-event [_ app]
    (if-not (:kiintioiden-haku-kaynnissa? app)
      (-> app
          (tuck-tyokalut/palvelukutsu :hae-kiintiot
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

  LiitaToimenpiteetKiintioon
  (process-event [_ app]
    (if-not (:kiintioon-liittaminen-kaynnissa? app)
      (-> app
          (tuck-tyokalut/palvelukutsu :liita-toimenpiteet-kiintioon
                                      {} ;; TODO PARAMS
                                      {:onnistui ->ToimenpiteetLiitettyKiintioon
                                       :epaonnistui ->ToimenpiteetEiLiitettyKiintioon})
          (assoc :kiintioon-liittaminen-kaynnissa? true))
      app))

  ToimenpiteetLiitettyKiintioon
  (process-event [_ app]
    (assoc app :kiintioon-liittaminen-kaynnissa? false))

  ToimenpiteetEiLiitettyKiintioon
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden liittäminen kiintiöön epäonnistui!" :danger)
    (assoc app :kiintioon-liittaminen-kaynnissa? false)))