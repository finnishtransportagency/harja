(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log error]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [cljs.core.async :as async :refer [<!]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [cljs.spec.alpha :as s]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu])
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
         :haku-kaynnissa? false
         :infolaatikko-nakyvissa? false
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
(defrecord SiirraValitutYksikkohintaisiin [])
(defrecord ToimenpiteetEiHaettu [virhe])

(defn kyselyn-hakuargumentit [valinnat]
  (merge (jaettu/kyselyn-hakuargumentit valinnat) {:tyyppi :kokonaishintainen}))

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
    (go (let [valitut (set (map ::to/id (jaettu/valitut-toimenpiteet (:toimenpiteet app))))
              vastaus (<! (k/post! :siirra-toimenpiteet-yksikkohintaisiin
                                   {::tot/urakka-id (get-in app [:valinnat :urakka-id])
                                    ::to/toimenpide-idt valitut}))]
          (when-not (k/virhe? vastaus)
            ;; TODO Tee... jotain...
            )))
    app)


  HaeToimenpiteet
  ;; Hakee toimenpiteet annetuilla valinnoilla. Jos valintoja ei anneta, käyttää tilassa olevia valintoja.
  (process-event [{valinnat :valinnat} app]
    (if-not (:haku-kaynnissa? app)
      (let [tulos! (tuck/send-async! ->ToimenpiteetHaettu)
            fail! (tuck/send-async! ->ToimenpiteetEiHaettu)]
        (try
          (let [hakuargumentit (kyselyn-hakuargumentit valinnat)]
            (if (s/valid? ::to/hae-vesivaylien-toimenpiteet-kysely hakuargumentit)
              (do
                (go
                  (let [vastaus (<! (k/post! :hae-kokonaishintaiset-toimenpiteet hakuargumentit))]
                    (if (k/virhe? vastaus)
                      (fail! vastaus)
                      (tulos! vastaus))))
                (assoc app :haku-kaynnissa? true))
              (log "Hakuargumentit eivät ole validit: " (s/explain-str ::to/hae-vesivaylien-toimenpiteet-kysely hakuargumentit))))
          (catch :default e
            (fail! nil)
            (throw e))))

      app))

  ToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (assoc app :toimenpiteet toimenpiteet
               :haku-kaynnissa? false))

  ToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :haku-kaynnissa? false)))