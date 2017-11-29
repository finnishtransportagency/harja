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
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.domain.muokkaustiedot :as m]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; todo:
;; - testit
;; huomioita:
;; terminologiaa: vv_hinnoittelu-taulu <-> tämän ns:n Hintaryhmä
;;                koko hintatiedot ja kan_hinta <-> tämän ns:n Hinnoittelu (TallennaHinnoittelu jne)
;;                kan_hinta <-> Hinnoittelu
;;                kan_hinta.ryhma <-> ?? - ei mitään tekemistä tämän ns:n Hintaryhmien kanssa kuitenkaan

(def tila (atom {:nakymassa? false
                 :toimenpiteiden-haku-kaynnissa? false
                 :suunnitellut-tyot nil
                 :suunniteltujen-toiden-haku-kaynnissa? false}))



(def alustettu-toimenpiteen-hinnoittelu
  {::toimenpide/id nil
   ::hinta/hinnat nil
   ::tyo/tyot []})

(defonce valinnat
  (reaction
   (log "valinnat-reaktio päivittyy - tila: " (pr-str tila))
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


;; Suunnitellut työt
(defrecord TyhjennaSuunnitellutTyot [])
(defrecord HaeSuunnitellutTyot [])
(defrecord SuunnitellutTyotHaettu [vastaus])
(defrecord SuunnitellutTyotEiHaettu [])

(defn etsi-mapit [mapit avain viitearvo]
  {:pre [(every? map? mapit)]
   :post [(every? map? %)]}

  (for [kandidaatti mapit
        :let [arvo (get kandidaatti avain)]
        :when (= viitearvo arvo)]
    kandidaatti))

(defn etsi-eka-map [mapit avain viitearvo]
  (first (etsi-mapit mapit avain viitearvo)))

(defn ilman-poistettuja [mapit-muokkaustiedoilla]
  {:pre [(every? map? mapit-muokkaustiedoilla)]
   :post [(every? map? %)]}
  (let [ok-mapit (remove ::m/poistettu? mapit-muokkaustiedoilla)]
    ok-mapit))

(defn hintaryhman-tyot [app ryhma-kriteeri]
  (let [tyo-hinnat (etsi-mapit (get-in app [:hinnoittele-toimenpide ::hinta/hinnat])
                               ::hinta/ryhma ryhma-kriteeri)]
    (ilman-poistettuja tyo-hinnat)))

(defn muut-tyot [app]
  (hintaryhman-tyot app "tyo"))

(defn muut-hinnat [app]
  (hintaryhman-tyot app "muu"))

(defn hinta-otsikolla [hinnat otsikkokriteeri]
  (etsi-eka-map hinnat ::hinta/otsikko otsikkokriteeri))


;; Toimenpiteen hinnoittelun yhteydessä tarjottavat vakiokentät (vectori, koska järjestys tärkeä)
(def vakiohinnat ["Yleiset materiaalit" "Matkakulut" "Muut kulut"])

(defn vakiohintakentta? [otsikko]
  (boolean ((set vakiohinnat) otsikko)))

(defn ainoa-otsikon-vakiokentta? [hinnat otsikko]
  (and
   (vakiohintakentta? otsikko)
   (-> (group-by ::hinta/otsikko hinnat)
       (get otsikko)
       count
       (= 1))))

(defn- hintakentta
  [hinta]
  (merge
   {::hinta/summa (cond
                    (or (nil? (::hinta/ryhma hinta))
                        (#{"muu"} (::hinta/ryhma hinta)))
                    0

                    (= "tyo" (::hinta/ryhma hinta))
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
                            ::hinta/ryhma "muu"}
                           olemassa-oleva-hinta))))
                     vakiohinnat)
        ;; Loput kentät ovat käyttäjän itse lisäämiä
        (map
         hintakentta
         (remove #((set vakiohinnat) (::hinta/otsikko %))
                 hinnat)))))

(defn- lisaa-hintarivi-toimenpiteelle* [id-avain tyot-tai-hinnat kentta-fn app]
  (let [jutut (get-in app [:hinnoittele-toimenpide tyot-tai-hinnat])
        idt (map id-avain jutut)
        seuraava-vapaa-id (dec (apply min (conj idt 0)))
        paivitetyt (conj jutut (kentta-fn seuraava-vapaa-id))]
    (log "lisaa-hintarivi-toimenpiteelle* - seuraava-vapaa-id" seuraava-vapaa-id)
    (log "tyot-tai-hinnat=" (pr-str tyot-tai-hinnat))
    (log "vanha " (pr-str jutut) " -> uudet: " (pr-str paivitetyt))
    (assoc-in app [:hinnoittele-toimenpide tyot-tai-hinnat] paivitetyt)))

(defn lisaa-hintarivi-toimenpiteelle
  ([app] (lisaa-hintarivi-toimenpiteelle {} app))
  ([hinta app]
   (lisaa-hintarivi-toimenpiteelle*
    ::hinta/id
    ::hinta/hinnat
    (fn [id]
      (hintakentta
       (merge {::hinta/id id} hinta)))
    app)))


(defn lisaa-tyorivi-toimenpiteelle
  ([app] (lisaa-tyorivi-toimenpiteelle {} app))
  ([tyo app]
   (lisaa-hintarivi-toimenpiteelle*
    ::tyo/id
    ::tyo/tyot
    (fn [id]
      (merge {::tyo/id id ::tyo/maara 0} tyo))
    app)))

(defn- poista-hintarivi-toimenpiteelta* [id id-avain tyot-tai-hinnat app]
  (let [rivit (get-in app [:hinnoittele-toimenpide tyot-tai-hinnat])
        paivitetyt
        (if-not (id-olemassa? id)
          (filterv #(not= (id-avain %) id) rivit)

          (mapv #(if (= (id-avain %) id)
                   (assoc % ::m/poistettu? true)
                   %)
                rivit))]
    (log "poista-hintarivi: paivitetyt " (count paivitetyt) " rivit " (count rivit))
    (assoc-in app [:hinnoittele-toimenpide tyot-tai-hinnat] paivitetyt)))

(defn poista-tyorivi-toimenpiteelta [id app]
  (poista-hintarivi-toimenpiteelta* id ::tyo/id ::tyo/tyot app))

(defn poista-hintarivi-toimenpiteelta [id app]
  (poista-hintarivi-toimenpiteelta* id ::hinta/id ::hinta/hinnat app))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  PaivitaValinnat
  (process-event [{valinnat :valinnat} app]
    (let [haku (tuck/send-async! ->HaeToimenpiteet)]
      (go (haku valinnat))
      (if-not (get-in app [:valinnat :urakka :id])
        (log "huonot valinnat, puuttuu urakka-id"))
      (assoc app :valinnat valinnat)))

  TyhjennaSuunnitellutTyot
  (process-event [_ app]
    (assoc app :suunnitellut-tyot nil))

  HaeSuunnitellutTyot
  (process-event [_ app]
    (let [urakka-id (get-in app [:valinnat :urakka :id])
          haku-ei-kaynnissa (not (:suunniteltujen-toiden-haku-kaynnissa? app))]
      (if (and haku-ei-kaynnissa (some? urakka-id))
        (do (tuck-apurit/post! :yksikkohintaiset-tyot
                                 {:urakka urakka-id}
                                 {:onnistui ->SuunnitellutTyotHaettu
                                         :epaonnistui ->SuunnitellutTyotEiHaettu})
            (assoc app :suunniteltujen-toiden-haku-kaynnissa? true))
        ;; else
        (do
          ;; (log "HaeSuunnitellutTyot: ei haeta, koska" (pr-str [urakka-id haku-ei-kaynnissa]))
          app))))

  SuunnitellutTyotHaettu
  (process-event [{vastaus :vastaus} app]
    (assoc app :suunnitellut-tyot (remove (comp nil? :yksikkohinta) vastaus)
               :suunniteltujen-toiden-haku-kaynnissa? false))

  SuunnitellutTyotEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Suunniteltujen töiden haku epäonnistui!" :danger)
    (assoc app :suunniteltujen-toiden-haku-kaynnissa? false))

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

    (let [hinnoiteltava-toimenpide (etsi-eka-map (:toimenpiteet app) ::toimenpide/id toimenpide-id)
          hinnat (or (::toimenpide/hinnat hinnoiteltava-toimenpide) [])
          tyot (or (::toimenpide/tyot hinnoiteltava-toimenpide) [])
          urakka-id (get-in app [:valinnat :urakka :id])]
      (if urakka-id
        (assoc app :hinnoittele-toimenpide
               {::toimenpide/id toimenpide-id
                ::hinta/hinnat (toimenpiteen-hintakentat hinnat)
                ::tyo/tyot tyot
                :urakka urakka-id})
        (do
          (log "ei aloiteta hinnoittelua, koska ei tiedetä urakkaa - valinnat: " (pr-str (:valinnat app)))
          (log "valinnat atomissa:" (pr-str (deref valinnat)))
          app))))

  LisaaHinnoiteltavaTyorivi
  (process-event [_ app]
    (lisaa-tyorivi-toimenpiteelle app))

  PeruToimenpiteenHinnoittelu
  (process-event [_ app]
    (assoc app
           :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false
           :hinnoittele-toimenpide alustettu-toimenpiteen-hinnoittelu))


  AsetaHintakentalleTiedot
  (process-event [{tiedot :tiedot} app]
    (when-not (::hinta/id tiedot)
      (log "AsetaHintakentalleTiedot kutsuttu huonolla id:llä, tiedot:" (pr-str tiedot)))
    (assoc-in app [:hinnoittele-toimenpide ::hinta/hinnat]
              (hinta/paivita-hintajoukon-hinnan-tiedot-idlla (get-in app [:hinnoittele-toimenpide
                                                                          ::hinta/hinnat]) tiedot)))

  AsetaTyorivilleTiedot
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-toimenpide ::tyo/tyot]
              (tyo/paivita-tyon-tiedot-idlla (get-in app [:hinnoittele-toimenpide ::tyo/tyot])
                                             tiedot)))

  LisaaHinnoiteltavaTyorivi
  (process-event [_ app]
    (lisaa-tyorivi-toimenpiteelle app))

  LisaaMuuKulurivi
  (process-event [_ app]
    (lisaa-hintarivi-toimenpiteelle
      {::hinta/ryhma "muu"}
      app))

  LisaaMuuTyorivi
  (process-event [_ app]
    (lisaa-hintarivi-toimenpiteelle
      {::hinta/ryhma "tyo"}
      app))

  PoistaHinnoiteltavaTyorivi
  (process-event [{tyo :tyo} app]
    (log "poista tyorivi: id " (::tyo/id tyo))
    (poista-tyorivi-toimenpiteelta (::tyo/id tyo) app))

  PoistaHinnoiteltavaHintarivi
  (process-event [{hinta :hinta} app]
    (poista-hintarivi-toimenpiteelta (::hinta/id hinta) app))

  TallennaToimenpiteenHinnoittelu
  (process-event [{tiedot :tiedot} app]
    (if-not (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? app)
      (do (tuck-apurit/post!
            :tallenna-kanavatoimenpiteen-hinnoittelu
            {::toimenpide/urakka-id (get-in app [:valinnat :urakka :id])
             ::toimenpide/id (get-in app [:hinnoittele-toimenpide ::toimenpide/id])
             ::hinta/tallennettavat-hinnat (get-in app [:hinnoittele-toimenpide ::hinta/hinnat])
             ::tyo/tallennettavat-tyot (get-in app [:hinnoittele-toimenpide ::tyo/tyot])}
            {:onnistui ->ToimenpiteenHinnoitteluTallennettu
             :epaonnistui ->ToimenpiteenHinnoitteluEiTallennettu})
          (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? true))
      app))

  ToimenpiteenHinnoitteluTallennettu
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Hinnoittelu tallennettu!" :success)
    (let [paivitetyt-toimenpiteet (mapv
                                   (fn [toimenpide]
                                     (if (= (::toimenpide/id toimenpide) (::toimenpide/id vastaus))
                                       vastaus
                                       toimenpide))
                                   (:toimenpiteet app))]

      (assoc app :toimenpiteet paivitetyt-toimenpiteet
                 :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false
                 :hinnoittele-toimenpide alustettu-toimenpiteen-hinnoittelu)))

  ToimenpiteenHinnoitteluEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Hinnoittelun tallennus epäonnistui!" :danger)
    (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false)))
