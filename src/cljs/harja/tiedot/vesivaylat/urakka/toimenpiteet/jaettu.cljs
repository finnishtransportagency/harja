(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log error]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [cljs.core.async :refer [<!]]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def valintojen-avaimet [:urakka-id :sopimus-id :aikavali
                         :vaylatyyppi :vayla
                         :tyolaji :tyoluokka :toimenpide
                         :vain-vikailmoitukset?])

(defn arvot-pudotusvalikko-valinnoiksi [kartta]
  (into [nil] (distinct (vals kartta))))

(defn- toimenpiteet-tyolajilla [toimenpiteet tyolaji]
  (filterv #(= (::to/tyolaji %) tyolaji) toimenpiteet))

(defn kaikki-valittu? [tyolajin-toimenpiteet]
  (every? true? (map :valittu? tyolajin-toimenpiteet)))

(defn mitaan-ei-valittu? [tyolajin-toimenpiteet]
  (every? (comp not true?)
          (map :valittu? tyolajin-toimenpiteet)))

(defn valitut-toimenpiteet [toimenpiteet]
  (filter :valittu? toimenpiteet))

(defn poista-toimenpiteet [toimenpiteet poistettavat-toimenpide-idt]
  (filter #(not (poistettavat-toimenpide-idt (::to/id %))) toimenpiteet))

(defn valinnan-tila [tyolajin-toimenpiteet]
  (cond (kaikki-valittu? tyolajin-toimenpiteet) true
        (mitaan-ei-valittu? tyolajin-toimenpiteet) false
        :default :harja.ui.kentat/indeterminate))

(defn viesti-siirto-tehty [siirrettyjen-lkm]
  (str siirrettyjen-lkm " "
       (if (= 1 siirrettyjen-lkm) "toimenpide" "toimenpidettä")
       " siirretty."))

(defn kyselyn-hakuargumentit [{:keys [urakka-id sopimus-id aikavali
                                      vaylatyyppi vayla
                                      tyolaji tyoluokka toimenpide
                                      vain-vikailmoitukset?] :as valinnat}]
  (spec-apurit/poista-nil-avaimet {::to/urakka-id urakka-id
                                   ::to/sopimus-id sopimus-id
                                   ::va/vaylatyyppi vaylatyyppi
                                   ::to/vayla-id vayla
                                   ::to/reimari-tyolaji (when tyolaji (to/reimari-tyolaji-avain->koodi tyolaji))
                                   ::to/reimari-tyoluokat (when tyoluokka (to/reimari-tyoluokka-avain->koodi tyoluokka))
                                   ::to/reimari-toimenpidetyypit (when toimenpide (to/reimari-toimenpidetyyppi-avain->koodi toimenpide))
                                   :alku (first aikavali)
                                   :loppu (second aikavali)
                                   :vikailmoitukset? vain-vikailmoitukset?}))

(defn joku-valittu? [toimenpiteet]
  (some :valittu? toimenpiteet))

(defn yhdista-tilat! [mun-tila sen-tila]
  (swap! mun-tila update :valinnat #(merge % (:valinnat @sen-tila)))
  mun-tila)

(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseTyolaji [tiedot])
(defrecord ValitseVayla [tiedot])
(defrecord AsetaInfolaatikonTila [uusi-tila])
(defrecord ToimenpiteetSiirretty [toimenpiteet])
(defrecord ToimenpiteetEiSiirretty [])

(extend-protocol tuck/Event
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

  ToimenpiteetSiirretty
  (process-event [{toimenpiteet :toimenpiteet} app]
    (viesti/nayta! (viesti-siirto-tehty (count toimenpiteet)) :success)
    (assoc app :toimenpiteet (poista-toimenpiteet (:toimenpiteet app) toimenpiteet)
               :siirto-kaynnissa? false))

  ToimenpiteetEiSiirretty
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden siirto epäonnistui!" :danger)
    app))

(defn siirra-valitut! [palvelu app]
  (let [tulos! (tuck/send-async! ->ToimenpiteetSiirretty)
        fail! (tuck/send-async! ->ToimenpiteetEiSiirretty)]
    (go (let [valitut (set (map ::to/id (valitut-toimenpiteet (:toimenpiteet app))))
              vastaus (<! (k/post! palvelu
                                   {::to/urakka-id (get-in app [:valinnat :urakka-id])
                                    ::to/idt valitut}))]
          (if (k/virhe? vastaus)
            (fail! vastaus)
            (tulos! vastaus)))))
  (assoc app :siirto-kaynnissa? true))