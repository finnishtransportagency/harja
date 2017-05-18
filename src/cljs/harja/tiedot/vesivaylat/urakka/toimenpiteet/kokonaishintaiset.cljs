(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
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
(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseTyolaji [tiedot])
(defrecord ValitseVayla [tiedot])
(defrecord PaivitaValinnat [tiedot])
(defrecord AsetaInfolaatikonTila [uusi-tila])

(defrecord HaeToimenpiteet [valinnat])
(defrecord ToimenpiteetHaettu [toimenpiteet])
(defrecord ToimenpiteetEiHaettu [virhe])

(defn- kyselyn-hakuargumentit [{:keys [urakka-id sopimus-id aikavali
                                        vaylatyyppi vayla
                                        tyolaji tyoluokka toimenpide
                                        vain-vikailmoitukset?] :as valinnat}]
  (spec-apurit/poista-nil-avaimet {::tot/urakka-id urakka-id
                                   ::to/sopimus-id sopimus-id
                                   ::va/vaylatyyppi vaylatyyppi
                                   ::to/vayla-id vayla
                                   ::to/tyolaji tyolaji
                                   ::to/tyoluokka tyoluokka
                                   ::to/toimenpide toimenpide
                                   :alku (first aikavali)
                                   :loppu (second aikavali)
                                   :vikailmoitukset? vain-vikailmoitukset?
                                   :tyyppi :kokonaishintainen}))

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  PaivitaValinnat
  ;; Valintojen päivittäminen laukaisee aina myös kantahaun uusimmilla valinnoilla (ellei ole jo käynnissä),
  ;; jotta näkymä pysyy synkassa valintojen kanssa
  (process-event [{tiedot :tiedot} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys tiedot
                                             [:urakka-id :sopimus-id :aikavali
                                              :vaylatyyppi :vayla
                                              :vain-vikailmoitukset?
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
    (let [valinnat (if (empty? valinnat)
                     (:valinnat app)
                     valinnat)
          tulos! (tuck/send-async! ->ToimenpiteetHaettu)
          fail! (tuck/send-async! ->ToimenpiteetEiHaettu)]
      (when-not (:haku-kaynnissa? app)
        (go
          (try
            (let [hakuargumentit (kyselyn-hakuargumentit valinnat)]
              (if (s/valid? ::to/hae-kokonaishintaiset-toimenpiteet-kysely hakuargumentit)
                (let [vastaus (<! (k/post! :hae-kokonaishintaiset-toimenpiteet hakuargumentit))]
                  (if (k/virhe? vastaus)
                    (fail! vastaus)
                    (tulos! vastaus)))
                (do (error "Hakuargumentit eivät ole validit: " (pr-str hakuargumentit))
                    (s/explain ::to/hae-kokonaishintaiset-toimenpiteet-kysely hakuargumentit))))
            (catch :default e
              (fail! nil)
              (throw e)))))
      (assoc app :haku-kaynnissa? true)))

  ToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (assoc app :toimenpiteet toimenpiteet
               :haku-kaynnissa? false))

  ToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :haku-kaynnissa? false)))