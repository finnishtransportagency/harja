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
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
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

;; Hinnoittelu
(defrecord AloitaToimenpiteenHinnoittelu [toimenpide-id])
(defrecord PeruToimenpiteenHinnoittelu [])
(defrecord AsetaHintakentalleTiedot [tiedot])
(defrecord AsetaTyorivilleTiedot [tiedot])
(defrecord LisaaHinnoiteltavaTyorivi [])
(defrecord LisaaHinnoiteltavaKomponenttirivi [])
(defrecord LisaaMuuKulurivi [])
(defrecord LisaaMuuTyorivi [])
(defrecord PoistaHinnoiteltavaTyorivi [tyo])
(defrecord PoistaHinnoiteltavaHintarivi [hinta])
(defrecord TallennaToimenpiteenHinnoittelu [tiedot])
(defrecord ToimenpiteenHinnoitteluTallennettu [vastaus])
(defrecord ToimenpiteenHinnoitteluEiTallennettu [virhe])

(defn etsi-map [mapit avain viitearvo]
  (first
   (for [kandidaatti mapit
         :let [arvo (get kandidaatti avain)]
         :when (= viitearvo arvo)]
     kandidaatti)))



(defn hinta-otsikolla [hinnat otsikkokriteeri]
  (etsi-map hinnat ::hinta/otsikko otsikkokriteeri))
;; Toimenpiteen hinnoittelun yhteydessä tarjottavat vakiokentät (vectori, koska järjestys tärkeä)
(def vakiohinnat ["Yleiset materiaalit" "Matkakulut" "Muut kulut"])

(defn- hintakentta
  [hinta]
  (merge
   {::hinta/summa (cond
                    (or (nil? (::hinta/ryhma hinta))
                        (#{:muu} (::hinta/ryhma hinta)))
                    0

                    (#{:komponentti :tyo} (::hinta/ryhma hinta))
                    nil)
    ::hinta/yleiskustannuslisa 0
    ::hinta/otsikko ""}
   hinta))

(defn- toimenpiteen-hintakentat [hinnat]
  (vec (concat
        ;; Vakiohintakentät näytetään aina riippumatta siitä onko niille annettu hintaa
        (map-indexed (fn [index otsikko]
                       (let [olemassa-oleva-hinta (hinta-otsikolla hinnat otsikko)]
                         (hintakentta
                          (merge
                           {::hinta/id (dec (- index))
                            ::hinta/otsikko otsikko
                            ::hinta/ryhma :muu}
                           olemassa-oleva-hinta))))
                     vakiohinnat)
        ;; Loput kentät ovat käyttäjän itse lisäämiä
        (map
         hintakentta
         (remove #((set vakiohinnat) (::hinta/otsikko %))
                 hinnat)))))

(defn- lisaa-hintarivi-toimenpiteelle* [id-avain kentta-fn app]
  (let [jutut (get-in app [:hinnoittele-toimenpide :tyot-tai-hinnat-placeholder])
        idt (map id-avain jutut)
        seuraava-vapaa-id (dec (apply min (conj idt 0)))
        paivitetyt (conj jutut (kentta-fn seuraava-vapaa-id))]
    (assoc-in app [:hinnoittele-toimenpide tyot-tai-hinnat] paivitetyt)))

(defn lisaa-hintarivi-toimenpiteelle
  ([app] (lisaa-hintarivi-toimenpiteelle {} app))
  ([hinta app]
   (lisaa-hintarivi-toimenpiteelle*
    ::hinta/id
    ;; ::h/hinnat
    (fn [id]
      (hintakentta
       (merge {::hinta/id id} hinta)))
    app)))


(defn lisaa-tyorivi-toimenpiteelle
  ([app] (lisaa-tyorivi-toimenpiteelle {} app))
  ([tyo app]
   (lisaa-hintarivi-toimenpiteelle*
    ::tyo/id
    ;; ::hinta/tyot
    (fn [id]
      (merge {::tyo/id id ::tyo/maara 0} tyo))
    app)))

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

  AloitaToimenpiteenHinnoittelu
  (process-event [{toimenpide-id :toimenpide-id} app]
    app
    (let [hinnoiteltava-toimenpide (etsi-map (:toimenpiteet map) ::toimenpide/id toimenpide-id)
          toimenpiteen-oma-hinnoittelu nil ;; (::toimenpide/oma-hinnoittelu hinnoiteltava-toimenpide)
          hinnat (or (::hinta/hinnat toimenpiteen-oma-hinnoittelu) [])
          tyot (or (::hinta/tyot toimenpiteen-oma-hinnoittelu) [])]
      (assoc app :hinnoittele-toimenpide
             {::toimenpide/id toimenpide-id
              ::hinta/hinnat (toimenpiteen-hintakentat hinnat)
              ::hinta/tyot tyot})))

  LisaaHinnoiteltavaTyorivi
  (process-event [_ app]
    (lisaa-tyorivi-toimenpiteelle app))

  PeruToimenpiteenHinnoittelu
  (process-event [_ app]
    (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false
           ;; :hinnoittele-toimenpide alustettu-toimenpiteen-hinnoittelu
           ))

  )
