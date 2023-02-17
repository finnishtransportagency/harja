(ns harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :as async]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log warn tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as u]
            [harja.domain.urakka :as urakka]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.vesivaylat.materiaali :as materiaalit]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.domain.kanavat.kommentti :as kommentti]
            [harja.domain.muokkaustiedot :as m]
            [harja.tiedot.navigaatio :as nav]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka :as urakkatiedot])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; terminologiaa: vv_hinnoittelu-taulu <-> tämän ns:n Hintaryhmä
;;                koko hintatiedot ja kan_hinta <-> tämän ns:n Hinnoittelu (TallennaHinnoittelu jne)
;;                kan_hinta <-> Hinnoittelu
;;                kan_hinta.ryhma <-> ?? - ei mitään tekemistä tämän ns:n Hintaryhmien kanssa kuitenkaan

(def tila (atom {:nakymassa? false
                 :toimenpiteiden-siirto-kaynnissa? false
                 :valitut-toimenpide-idt #{}
                 :avattu-toimenpide nil
                 :toimenpiteet nil
                 :materiaalien-haku-kaynnissa? false
                 :toimenpiteiden-haku-kaynnissa? false
                 :suunniteltujen-toiden-haku-kaynnissa? false
                 :suunnitellut-tyot nil}))



(def alustettu-toimenpiteen-hinnoittelu
  {::toimenpide/id nil
   ::hinta/hinnat nil
   ::tyo/tyot []})

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
;; Lomake
(defrecord UusiToimenpide [])
(defrecord TyhjennaAvattuToimenpide [])
(defrecord AsetaLomakkeenToimenpiteenTiedot [toimenpide])
(defrecord TallennaToimenpide [toimenpide poisto?])
(defrecord ToimenpideTallennettu [vastaus poisto?])
(defrecord ToimenpiteenTallentaminenEpaonnistui [tulos poisto?])
(defrecord PoistaToimenpide [toimenpide])
(defrecord HuoltokohteetHaettu [huoltokohteet])
(defrecord HuoltokohteidenHakuEpaonnistui [])
(defrecord KytkePaikannusKaynnissa [])
;; UI-toiminnot
(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseToimenpiteet [tiedot])
(defrecord SiirraValitut [])
(defrecord ValitutSiirretty [])
(defrecord ValitutEiSiirretty [])

;; Materiaalit
(defrecord HaeMateriaalit [])
(defrecord MuokkaaMateriaaleja [materiaalit])
(defrecord MateriaalitHaettu [materiaalit])
(defrecord MateriaalienHakuEpaonnistui [])
(defrecord LisaaMateriaali [])
(defrecord LisaaVirhe [virhe])

;; Hinnoittelu
(defrecord AloitaToimenpiteenHinnoittelu [toimenpide-id])
(defrecord PeruToimenpiteenHinnoittelu [])
(defrecord AsetaHintakentalleTiedot [tiedot])
(defrecord AsetaTyorivilleTiedot [tiedot])
(defrecord LisaaHinnoiteltavaTyorivi [])
(defrecord LisaaHinnoiteltavaKomponenttirivi [])
(defrecord LisaaOmakustannushintainenTyorivi [])
(defrecord LisaaMuuKulurivi [])
(defrecord LisaaMuuTyorivi [])
(defrecord LisaaMateriaaliKulurivi [])
(defrecord PoistaHinnoiteltavaTyorivi [tyo])
(defrecord PoistaHinnoiteltavaHintarivi [hinta])
(defrecord TallennaToimenpiteenHinnoittelu [tiedot])
(defrecord ToimenpiteenHinnoitteluTallennettu [vastaus])
(defrecord ToimenpiteenHinnoitteluEiTallennettu [virhe])
(defrecord KommentoiToimenpiteenHinnoittelua [tila kommentti toimenpide-id])
(defrecord ToimenpiteenKommenttiTallennettu [vastaus])
(defrecord ToimenpiteenKommenttiEiTallennettu [virhe])
(defrecord HaeHuoltokohteet [])
(defrecord HinnoiteltavanToimenpiteenMateriaalit [])

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

(defn omakustannushintaiset-tyot [app]
  (hintaryhman-tyot app "oma"))

(defn muut-tyot [app]
  (hintaryhman-tyot app "tyo"))

(defn muut-hinnat [app]
  (hintaryhman-tyot app "muu"))

(declare materiaalisaldo-hinnalle)

(defn liita-yksikot-materiaalihintaan [app materiaali-hinta]
  (let [urakan-materiaalit (:urakan-materiaalit app)
        materiaalisaldo (materiaalisaldo-hinnalle materiaali-hinta urakan-materiaalit)]
    (if materiaalisaldo
      (assoc materiaali-hinta ::hinta/yksikko (::materiaalit/yksikko materiaalisaldo))
      ;; else
      materiaali-hinta)))

(defn materiaalit [app]
  (hintaryhman-tyot app "materiaali"))

(defn liita-hintoihin-saldojen-yksikot [app hinnat]
  (mapv (partial liita-yksikot-materiaalihintaan app) hinnat))

(defn hinta-otsikolla [hinnat otsikkokriteeri]
  (etsi-eka-map hinnat ::hinta/otsikko otsikkokriteeri))

(defn toimenpiteen-materiaalit
  "Palauttaa hinnoiteltavan rivin materiaalit."
  [{toimenpide-id :toimenpide-id materiaalit :materiaalit}]
  (assert (integer? toimenpide-id) "toimenpide-id pitää olla int")
  (flatten
    (map (fn [materiaali]
           (keep #(when (= (::materiaalit/toimenpide %) toimenpide-id)
                    {:nimi (::materiaalit/nimi materiaali)
                     :maara (- (::materiaalit/maara %))
                     :materiaali-id (::materiaalit/id %)
                     :yksikko (::materiaalit/yksikko materiaali)})
                 (::materiaalit/muutokset materiaali)))
         materiaalit)))


(defn materiaalisaldo-hinnalle
  [materiaali-hinta materiaalit]
  (some (fn [materiaali]
          (when  (and
                  (= "materiaali" (::hinta/ryhma materiaali-hinta))
                  (= (::materiaalit/nimi materiaali)
                     (::hinta/otsikko materiaali-hinta)))
            materiaali))
        materiaalit))

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
  (let [;; Vakiohintakentät näytetään aina riippumatta siitä onko niille annettu hintaa
        vakiokenttien-hinnat (map-indexed (fn [index otsikko]
                                            (let [olemassa-oleva-hinta (hinta-otsikolla hinnat otsikko)]
                                              (hintakentta
                                                (merge
                                                  {::hinta/id (dec (- index))
                                                   ::hinta/otsikko otsikko
                                                   ::hinta/ryhma "muu"}
                                                  olemassa-oleva-hinta))))
                                          vakiohinnat)
        hinnattomien-vakiokenttien-lukumaara (count (filter #(< (::hinta/id %) 0)
                                                            vakiokenttien-hinnat))
        ;; Toimenpiteellä käytetyt materiaalit näytetään aina
        materiaalien-hinnat (reduce (fn [kertynyt hinta]
                                      (if (= (::hinta/ryhma hinta) "materiaali")
                                        (conj kertynyt
                                              (hintakentta
                                                (merge {::hinta/id (dec (- (+ (count kertynyt) hinnattomien-vakiokenttien-lukumaara)))}
                                                       hinta)))
                                        kertynyt))
                                    [] hinnat)
        ;; Loput kentät ovat käyttäjän itse lisäämiä
        loppujen-hinnat (map
                          hintakentta
                          (remove #((set (concat vakiohinnat (map ::hinta/otsikko materiaalien-hinnat))) (::hinta/otsikko %))
                                  hinnat))]
    (vec (concat vakiokenttien-hinnat materiaalien-hinnat loppujen-hinnat))))

(defn- lisaa-hintarivi-toimenpiteelle* [id-avain tyot-tai-hinnat kentta-fn app]
  (let [jutut (get-in app [:hinnoittele-toimenpide tyot-tai-hinnat])
        idt (map id-avain jutut)
        seuraava-vapaa-id (dec (apply min (conj idt 0)))
        paivitetyt (conj jutut (kentta-fn seuraava-vapaa-id))]
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
    (assoc-in app [:hinnoittele-toimenpide tyot-tai-hinnat] paivitetyt)))

(defn poista-tyorivi-toimenpiteelta [id app]
  (poista-hintarivi-toimenpiteelta* id ::tyo/id ::tyo/tyot app))

(defn poista-hintarivi-toimenpiteelta [id app]
  (poista-hintarivi-toimenpiteelta* id ::hinta/id ::hinta/hinnat app))

(defn hinnoittelun-voi-tallentaa? [app]
  (and (every? #(and (some? (::tyo/toimenpidekoodi-id %))
                     (some? (::tyo/maara %)))
               (get-in app [:hinnoittele-toimenpide ::tyo/tyot]))
       (every? #(or (some? (::hinta/summa %))
                    (and (some? (::hinta/maara %))
                         (some? (::hinta/yksikkohinta %))
                         (not-empty (::hinta/yksikko %)))
                    (and (some? (::hinta/maara %))
                         (some? (::hinta/yksikkohinta %))
                         (= "materiaali" (::hinta/ryhma %))))
               (get-in app [:hinnoittele-toimenpide ::hinta/hinnat]))
       (every? #(not-empty (::hinta/otsikko %))
               (get-in app [:hinnoittele-toimenpide ::hinta/hinnat]))))

(defn materiaali->hinta
  [{:keys [nimi maara materiaali-id yksikko]}]
  {::hinta/otsikko nimi
   ::hinta/ryhma "materiaali"
   ::hinta/maara maara
   ::hinta/yksikkohinta 0
   ::hinta/materiaali-id materiaali-id
   ::hinta/yksikko yksikko})

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (merge app
           {:nakymassa? nakymassa?}
           (when nakymassa?
             {:tehtavat (toimenpiteet/tehtavat-tyypilla @urakkatiedot/urakan-toimenpiteet-ja-tehtavat
                                                        "muutoshintainen")
              :toimenpideinstanssit @urakkatiedot/urakan-toimenpideinstanssit})))

  PaivitaValinnat
  (process-event [{valinnat :valinnat} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                valinnat)
          tp-haku (tuck/send-async! ->HaeToimenpiteet)
          ml-haku (tuck/send-async! ->HaeMateriaalit)]
      (go (tp-haku uudet-valinnat))
      (go (ml-haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  TyhjennaSuunnitellutTyot
  (process-event [_ app]
    (assoc app :suunnitellut-tyot nil))

  HaeSuunnitellutTyot
  (process-event [_ app]
    (let [urakka-id (get-in app [:valinnat :urakka :id])
          haku-ei-kaynnissa (not (:suunniteltujen-toiden-haku-kaynnissa? app))]
      (if (and haku-ei-kaynnissa (some? urakka-id))
        (do (tuck-apurit/post! :yksikkohintaiset-tyot
                               urakka-id
                               {:onnistui ->SuunnitellutTyotHaettu
                                :epaonnistui ->SuunnitellutTyotEiHaettu})
            (assoc app :suunniteltujen-toiden-haku-kaynnissa? true))
        app)))

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
      (let [argumentit (toimenpiteet/muodosta-kohteiden-hakuargumentit valinnat :muutos-lisatyo)]
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

  ValitseToimenpide
  (process-event [{tiedot :tiedot} app]
    (let [toimenpide-id (:id tiedot)
          valittu? (:valittu? tiedot)
          aseta-valinta (if valittu? conj disj)]
      (assoc app :valitut-toimenpide-idt
                 (aseta-valinta (:valitut-toimenpide-idt app) toimenpide-id))))

  ValitseToimenpiteet
  (process-event [{tiedot :tiedot} app]
    (let [kaikki-valittu? (:kaikki-valittu? tiedot)]
      (if kaikki-valittu?
        (assoc app :valitut-toimenpide-idt
                   (set (map ::toimenpide/id (:toimenpiteet app))))
        (assoc app :valitut-toimenpide-idt #{}))))

  SiirraValitut
  (process-event [_ app]
    (when-not (:toimenpiteiden-siirto-kaynnissa? app)
      (-> app
          (tuck-apurit/post! :siirra-kanavatoimenpiteet
                             {::toimenpide/toimenpide-idt (:valitut-toimenpide-idt app)
                              ::toimenpide/urakka-id (get-in app [:valinnat :urakka :id])
                              ::toimenpide/tyyppi :kokonaishintainen}
                             {:onnistui ->ValitutSiirretty
                              :epaonnistui ->ValitutEiSiirretty})
          (assoc :toimenpiteiden-siirto-kaynnissa? true))))

  ValitutSiirretty
  (process-event [_ app]
    (viesti/nayta! (toimenpiteet/toimenpiteiden-toiminto-suoritettu
                     (count (:valitut-toimenpide-idt app)) "siirretty") :success)
    (assoc app :toimenpiteiden-siirto-kaynnissa? false
               :valitut-toimenpide-idt #{}
               :toimenpiteet (filter
                               (fn [toimenpide]
                                 (not ((:valitut-toimenpide-idt app)
                                        (::toimenpide/id toimenpide))))
                               (:toimenpiteet app))))

  ValitutEiSiirretty
  (process-event [_ app]
    (viesti/nayta! "Siiro epäonnistui" :danger)
    (assoc app :toimenpiteiden-siirto-kaynnissa? false))

  AloitaToimenpiteenHinnoittelu
  (process-event [{toimenpide-id :toimenpide-id} app]

    (let [hinnoiteltava-toimenpide (etsi-eka-map (:toimenpiteet app) ::toimenpide/id toimenpide-id)
          materiaalit (or (toimenpiteen-materiaalit {:toimenpide-id toimenpide-id :materiaalit (:urakan-materiaalit app)}) [])
          hinnat (or (::toimenpide/hinnat hinnoiteltava-toimenpide) [])
          tallentamattomat-materiaali-hinnat (sequence (comp
                                                         (remove (fn [materiaali]
                                                                   (some #(= (:nimi materiaali)
                                                                             (::hinta/otsikko %))
                                                                         hinnat)))
                                                         (map materiaali->hinta))
                                                       materiaalit)
          yhdistetyt-hinnat (concat hinnat tallentamattomat-materiaali-hinnat)
          tyot (or (::toimenpide/tyot hinnoiteltava-toimenpide) [])
          urakka-id (get-in app [:valinnat :urakka :id])]
      (if urakka-id
        (assoc app :hinnoittele-toimenpide
                   {::toimenpide/id toimenpide-id
                    ::toimenpide/pvm (::toimenpide/pvm hinnoiteltava-toimenpide)
                    ::hinta/hinnat (toimenpiteen-hintakentat yhdistetyt-hinnat)
                    ::tyo/tyot tyot
                    :urakka urakka-id})
        (do
          (warn "Ei aloiteta hinnoittelua, koska ei tiedetä urakkaa - valinnat: " (pr-str (:valinnat app)))
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

  LisaaOmakustannushintainenTyorivi
  (process-event [_ app]
    (lisaa-hintarivi-toimenpiteelle
      {::hinta/ryhma "oma"}
      app))

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

  LisaaMateriaaliKulurivi
  (process-event [_ app]
    (lisaa-hintarivi-toimenpiteelle
      {::hinta/ryhma "materiaali"}
      app))

  PoistaHinnoiteltavaTyorivi
  (process-event [{tyo :tyo} app]
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
             ::hinta/tallennettavat-hinnat (liita-hintoihin-saldojen-yksikot app (get-in app [:hinnoittele-toimenpide ::hinta/hinnat]))
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
    (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false))

  KommentoiToimenpiteenHinnoittelua
  (process-event [{tila :tila
                   kommentti :kommentti
                   toimenpide-id :toimenpide-id} app]
    (if-not (:toimenpiteen-kommentin-tallennus-kaynnissa? app)
      (do (tuck-apurit/post!
            :tallenna-kanavatoimenpiteen-hinnoittelun-kommentti
            {::kommentti/toimenpide-id toimenpide-id
             ::kommentti/tila tila
             ::kommentti/kommentti kommentti
             ::toimenpide/urakka-id (get-in app [:valinnat :urakka :id])}
            {:onnistui ->ToimenpiteenKommenttiTallennettu
             :epaonnistui ->ToimenpiteenKommenttiEiTallennettu})
          (assoc app :toimenpiteen-kommentin-tallennus-kaynnissa? true))
      app))

  ToimenpiteenKommenttiTallennettu
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Hinnoittelu tallennettu!" :success)
    (let [paivitetyt-toimenpiteet (mapv
                                    (fn [toimenpide]
                                      (if (= (::toimenpide/id toimenpide) (::toimenpide/id vastaus))
                                        vastaus
                                        toimenpide))
                                    (:toimenpiteet app))]

      (assoc app :toimenpiteet paivitetyt-toimenpiteet
                 :toimenpiteen-kommentin-tallennus-kaynnissa? false
                 :hinnoittele-toimenpide alustettu-toimenpiteen-hinnoittelu)))

  ToimenpiteenKommenttiEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Hinnoittelun tallennus epäonnistui!" :danger)
    (assoc app :toimenpiteen-kommentin-tallennus-kaynnissa? false))

  UusiToimenpide
  (process-event [_ app]
    (toimenpiteet/uusi-toimenpide app @istunto/kayttaja @navigaatio/valittu-urakka))

  TyhjennaAvattuToimenpide
  (process-event [_ app]
    (toimenpiteet/tyhjenna-avattu-toimenpide app))

  AsetaLomakkeenToimenpiteenTiedot
  (process-event [{toimenpide :toimenpide} app]
    (toimenpiteet/aseta-lomakkeen-tiedot app toimenpide))

  TallennaToimenpide
  (process-event [{toimenpide :toimenpide poisto? :poisto?}
                  {valinnat :valinnat tehtavat :tehtavat :as app}]
    (toimenpiteet/tallenna-toimenpide app {:valinnat valinnat
                                           :tehtavat tehtavat
                                           :toimenpide toimenpide
                                           :poisto? poisto?
                                           :tyyppi :muutos-lisatyo
                                           :toimenpide-tallennettu ->ToimenpideTallennettu
                                           :toimenpide-ei-tallennettu ->ToimenpiteenTallentaminenEpaonnistui}))

  ToimenpideTallennettu
  (process-event [{vastaus :vastaus poisto? :poisto?} app]
    (toimenpiteet/toimenpide-tallennettu app (:kanavatoimenpiteet vastaus) (:materiaalilistaus vastaus) poisto?))

  ToimenpiteenTallentaminenEpaonnistui
  (process-event [{poisto? :poisto?} app]
    (toimenpiteet/toimenpide-ei-tallennettu app poisto?))

  PoistaToimenpide
  (process-event [{toimenpide :toimenpide} app]
    (let [tallennus! (tuck/send-async! ->TallennaToimenpide)]
      (go (tallennus! (assoc toimenpide ::muokkaustiedot/poistettu? true)
                      true))
      (update app :valitut-toimenpide-idt
              #(toimenpiteet/poista-valittu-toimenpide % (::toimenpide/id toimenpide)))))

  HaeHuoltokohteet
  (process-event [{toimenpide :toimenpide} app]
    (tuck-apurit/get! :hae-kanavien-huoltokohteet
                      {:onnistui ->HuoltokohteetHaettu
                       :epaonnistui ->HuoltokohteidenHakuEpaonnistui})
    app)

  HuoltokohteetHaettu
  (process-event [{huoltokohteet :huoltokohteet} app]
    (toimenpiteet/huoltokohteet-haettu app huoltokohteet))

  HuoltokohteidenHakuEpaonnistui
  (process-event [_ app]
    (toimenpiteet/huoltokohteet-ei-haettu app))

  KytkePaikannusKaynnissa
  (process-event [_ app]
    (update-in app [:avattu-toimenpide :paikannus-kaynnissa?] not))

  HaeMateriaalit
  (process-event [_ {:keys [materiaalien-haku-kaynnissa?] :as app}]
    (assert (some? materiaalien-haku-kaynnissa?) "huono tila: materiaalien-haku-kaynnissa? oli nil")
    (when-not materiaalien-haku-kaynnissa?
      (let [urakka-id (:id @navigaatio/valittu-urakka)]
        (-> app
            (tuck-apurit/post! :hae-vesivayla-materiaalilistaus
                               {::materiaalit/urakka-id urakka-id}
                               {:onnistui ->MateriaalitHaettu
                                :epaonnistui ->MateriaalienHakuEpaonnistui})
            (assoc :materiaalien-haku-kaynnissa? true
                   :urakan-materiaalit nil)))))

  MateriaalitHaettu
  (process-event [{materiaalit :materiaalit} app]
    ;; (log "MateriaalitHaettu, saatiin" (count materiaalit))
    (assoc app
      :urakan-materiaalit materiaalit
      :materiaalien-haku-kaynnissa? false))

  MateriaalienHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Materiaalien haku epäonnistui" :danger)
    (assoc app :materiaalien-haku-kaynnissa? false))

  MuokkaaMateriaaleja
  (process-event [{materiaalit :materiaalit} app]
    ;; urakan materiaaleista lisätyt voidaan tunnistaa muutokset-avaimella
    (if (:avattu-toimenpide app)
      (assoc-in app [:avattu-toimenpide ::materiaalit/materiaalit]
                (vec
                  (for [m materiaalit]
                    (if (-> m :materiaalit ::materiaalit/muutokset)
                      (update m :tallennetut-materiaalit dissoc ::materiaalit/muutokset ::materiaalit/id)
                      m))))
      app))

  LisaaMateriaali
  (process-event [_ app]
    ;; Materiaalien järjestystä varten täytyy käyttää järjestysnumeroa. Nyt ei voida käyttää muokkaus-gridin generoimaa
    ;; numeroa, koska rivinlisäysnappi ei ole normaali gridin lisäysnappi
    (update-in app
               [:avattu-toimenpide ::materiaalit/materiaalit]
               #(let [vanha-id (apply max (map :jarjestysnumero %))
                      uusi-id (if (nil? vanha-id) 0 (inc vanha-id))]
                  (conj (vec %) {:jarjestysnumero uusi-id}))))

  LisaaVirhe
  (process-event [{virhe :virhe} app]
    (assoc-in app [:avattu-toimenpide :materiaalit-taulukon-virheet] virhe)))
