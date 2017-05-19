(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log error]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.toteuma :as tot]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [cljs.core.async :refer [<!]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [cljs.spec.alpha :as s])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseTyolaji [tiedot])
(defrecord ValitseVayla [tiedot])
(defrecord PaivitaValinnat [tiedot])
(defrecord AsetaInfolaatikonTila [uusi-tila])

(defrecord HaeToimenpiteet [valinnat])
(defrecord ToimenpiteetHaettu [toimenpiteet])
(defrecord ToimenpiteetEiHaettu [virhe])

(defn kyselyn-hakuargumentit [{:keys [urakka-id sopimus-id aikavali
                                      vaylatyyppi vayla
                                      tyolaji tyoluokka toimenpide] :as valinnat}]
  ;; TODO Työlaji / työluokka / toimenpide avain voi mäppäytyä useaan koodiin,
  ;; tällöin pitää voida lähettää kaikki setissä
  (spec-apurit/poista-nil-avaimet {::tot/urakka-id urakka-id
                                   ::to/sopimus-id sopimus-id
                                   ::va/vaylatyyppi vaylatyyppi
                                   ::to/vayla-id vayla
                                   ::to/reimari-tyolaji (to/reimari-tyolaji-avain->koodi tyolaji)
                                   ::to/reimari-tyoluokka (to/reimari-tyoluokka-avain->koodi tyoluokka)
                                   ::to/reimari-toimenpide (to/reimari-toimenpide-avain->koodi toimenpide)
                                   :alku (first aikavali)
                                   :loppu (second aikavali)
                                   :tyyppi :yksikkohintainen}))

(extend-protocol tuck/Event

  PaivitaValinnat
  ;; Valintojen päivittäminen laukaisee aina myös kantahaun uusimmilla valinnoilla (ellei ole jo käynnissä),
  ;; jotta näkymä pysyy synkassa valintojen kanssa
  (process-event [{tiedot :tiedot} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys tiedot
                                             [:urakka-id :sopimus-id :aikavali
                                              :vaylatyyppi :vayla
                                              :tyolaji :tyoluokka :toimenpide]))
          haku (tuck/send-async! ->HaeToimenpiteet)]
      (haku uudet-valinnat)
      (assoc app :valinnat uudet-valinnat)))

  ValitseToimenpide
  (process-event [{tiedot :tiedot} {:keys [toimenpiteet] :as app}]
    (let [toimenpide-id (:id tiedot)
          valinta (:valinta tiedot)
          paivitetty-toimenpide (-> (to/toimenpide-idlla toimenpiteet toimenpide-id)
                                    (assoc :valittu? valinta))]
      (assoc app :toimenpiteet (mapv #(if (= (::to/id %) toimenpide-id) paivitetty-toimenpide %)
                                     toimenpiteet))))

  ValitseTyolaji
  (process-event [{tiedot :tiedot} {:keys [toimenpiteet] :as app}]
    (let [tyolaji (:tyolaji tiedot)
          valinta (:valinta tiedot)
          paivitetyt-toimenpiteet (mapv #(if (= (::to/tyolaji %) tyolaji)
                                           (assoc % :valittu? valinta)
                                           %)
                                        toimenpiteet)]
      (assoc app :toimenpiteet paivitetyt-toimenpiteet)))

  ValitseVayla
  (process-event [{tiedot :tiedot} {:keys [toimenpiteet] :as app}]
    (let [vayla-id (:vayla-id tiedot)
          valinta (:valinta tiedot)
          paivitetyt-toimenpiteet (mapv #(if (= (get-in % [::to/vayla ::va/id]) vayla-id)
                                           (assoc % :valittu? valinta)
                                           %)
                                        toimenpiteet)]
      (assoc app :toimenpiteet paivitetyt-toimenpiteet)))

  AsetaInfolaatikonTila
  (process-event [{uusi-tila :uusi-tila} app]
    (assoc app :infolaatikko-nakyvissa? uusi-tila))


  HaeToimenpiteet
  ;; Hakee toimenpiteet annetuilla valinnoilla. Jos valintoja ei anneta, käyttää tilassa olevia valintoja.
  (process-event [{valinnat :valinnat} app]
    (if-not (:haku-kaynnissa? app)
      (let [valinnat (if (empty? valinnat)
                       (:valinnat app)
                       valinnat)
            tulos! (tuck/send-async! ->ToimenpiteetHaettu)
            fail! (tuck/send-async! ->ToimenpiteetEiHaettu)]
        (try
          (let [hakuargumentit (kyselyn-hakuargumentit valinnat)]
            (if (s/valid? ::to/hae-vesivaylien-toimenpiteet-kyselyt hakuargumentit)
              (do
                (go
                  (let [vastaus (<! (k/post! :hae-yksikkohintaiset-toimenpiteet hakuargumentit))]
                    (if (k/virhe? vastaus)
                      (fail! vastaus)
                      (tulos! vastaus))))
                (assoc app :haku-kaynnissa? true))
              (log "Hakuargumentit eivät ole validit: " (s/explain-str ::to/hae-vesivaylien-toimenpiteet-kyselyt hakuargumentit))))
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