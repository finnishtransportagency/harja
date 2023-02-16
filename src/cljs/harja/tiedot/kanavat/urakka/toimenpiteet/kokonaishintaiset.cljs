(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.id :refer [id-olemassa?]]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.urakka :as urakka]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.vesivaylat.materiaali :as materiaalit]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.tiedot.urakka :as urakkatiedot]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as navigaatio])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; YLeiset
(defrecord NakymaAvattu [])
(defrecord NakymaSuljettu [])
(defrecord PaivitaValinnat [valinnat])
;; Haut
(defrecord HaeToimenpiteet [valinnat])
(defrecord ToimenpiteetHaettu [toimenpiteet])
(defrecord ToimenpiteidenHakuEpaonnistui [])
;; Lomake
(defrecord UusiToimenpide [])
(defrecord AsetaLomakkeenToimenpiteenTiedot [toimenpide])
(defrecord TyhjennaAvattuToimenpide [])
(defrecord ValinnatHaettuToimenpiteelle [valinnat])
(defrecord VirheTapahtui [virhe])
(defrecord HuoltokohteetHaettu [huoltokohteet])
(defrecord HuoltokohteidenHakuEpaonnistui [])
(defrecord TallennaToimenpide [toimenpide poisto?])
(defrecord ToimenpideTallennettu [vastaus poisto?])
(defrecord ToimenpiteenTallentaminenEpaonnistui [tulos poisto?])
(defrecord PoistaToimenpide [toimenpide])
(defrecord KytkePaikannusKaynnissa [])
;; Rivien valinta ja niiden toiminnot
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


(def tila (atom {:nakymassa? false
                 :valinnat nil
                 :avattu-toimenpide nil
                 :toimenpideinstanssit nil
                 :tehtavat nil
                 :huoltokohteet nil
                 :tallennus-kaynnissa? false
                 :haku-kaynnissa? false
                 :toimenpiteiden-siirto-kaynnissa? false
                 :materiaalien-haku-kaynnissa? false
                 :valitut-toimenpide-idt #{}
                 :toimenpiteet nil}))

(defn alkuvalinnat []
  {:urakka @navigaatio/valittu-urakka
   :sopimus-id (first @urakkatiedot/valittu-sopimusnumero)
   :aikavali @urakkatiedot/valittu-aikavali
   :toimenpide @urakkatiedot/valittu-toimenpideinstanssi})

(defonce valinnat
  (reaction
    (when (:nakymassa? @tila)
      (alkuvalinnat))))

(defn kokonashintaiset-tehtavat [tehtavat]
  (filter
    (fn [tehtava]
      (some #(= % "kokonaishintainen") (:hinnoittelu tehtava)))
    (map #(nth % 3) tehtavat)))

(extend-protocol tuck/Event
  NakymaAvattu
  (process-event [_ {:keys [huoltokohteiden-haku-kaynnissa?] :as app}]
    (if huoltokohteiden-haku-kaynnissa?
      (assoc app :nakymassa? true)
      (let [aseta-valinnat! (tuck/send-async! ->PaivitaValinnat (alkuvalinnat))]
        (go (aseta-valinnat!))
        (-> app
            (tuck-apurit/get! :hae-kanavien-huoltokohteet
                              {:onnistui ->HuoltokohteetHaettu
                               :epaonnistui ->HuoltokohteidenHakuEpaonnistui})
            (assoc :nakymassa? true
                   :huoltokohteiden-haku-kaynnissa? true
                   :tehtavat (toimenpiteet/tehtavat-tyypilla @urakkatiedot/urakan-toimenpiteet-ja-tehtavat
                                                             "kokonaishintainen")
                   :toimenpideinstanssit @urakkatiedot/urakan-toimenpideinstanssit
                   :huoltokohteet nil)))))

  NakymaSuljettu
  (process-event [_ app]
    (assoc app :nakymassa? false))

  PaivitaValinnat
  (process-event [{valinnat :valinnat} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                valinnat)
          tp-haku (tuck/send-async! ->HaeToimenpiteet)
          ml-haku (tuck/send-async! ->HaeMateriaalit)]
      (go (tp-haku uudet-valinnat))
      (go (ml-haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  HaeToimenpiteet
  (process-event [{valinnat :valinnat} app]
    (if (and
          (get-in valinnat [:urakka :id])
          (not (:haku-kaynnissa? app)))
      (let [argumentit (toimenpiteet/muodosta-kohteiden-hakuargumentit valinnat :kokonaishintainen)]
        (-> app
            (tuck-apurit/post! :hae-kanavatoimenpiteet
                               argumentit
                               {:onnistui ->ToimenpiteetHaettu
                                :epaonnistui ->ToimenpiteidenHakuEpaonnistui})
            (assoc :haku-kaynnissa? true)))
      app))

  ToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (assoc app :haku-kaynnissa? false
               :toimenpiteet toimenpiteet))

  ToimenpiteidenHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Kokonaishintaisten toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :haku-kaynnissa? false
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
                   (set (map ::kanavan-toimenpide/id (:toimenpiteet app))))
        (assoc app :valitut-toimenpide-idt #{}))))


  SiirraValitut
  (process-event [_ app]
    (if-not (:toimenpiteiden-siirto-kaynnissa? app)
      (-> app
          (tuck-apurit/post! :siirra-kanavatoimenpiteet
                             {::kanavan-toimenpide/toimenpide-idt (:valitut-toimenpide-idt app)
                              ::kanavan-toimenpide/urakka-id (get-in app [:valinnat :urakka :id])
                              ::kanavan-toimenpide/tyyppi :muutos-lisatyo}
                             {:onnistui ->ValitutSiirretty
                              :epaonnistui ->ValitutEiSiirretty})
          (assoc :toimenpiteiden-siirto-kaynnissa? true))
      app))

  ValitutSiirretty
  (process-event [_ app]
    (viesti/nayta! (toimenpiteet/toimenpiteiden-toiminto-suoritettu
                     (count (:valitut-toimenpide-idt app)) "siirretty") :success)
    (assoc app :toimenpiteiden-siirto-kaynnissa? false
               :valitut-toimenpide-idt #{}
               :toimenpiteet (filter
                               (fn [toimenpide]
                                 (not ((:valitut-toimenpide-idt app)
                                        (::kanavan-toimenpide/id toimenpide))))
                               (:toimenpiteet app))))

  ValitutEiSiirretty
  (process-event [_ app]
    (viesti/nayta! "Siiro epäonnistui" :danger)
    (assoc app :toimenpiteiden-siirto-kaynnissa? false))

  UusiToimenpide
  (process-event [_ app]
    (toimenpiteet/uusi-toimenpide app @istunto/kayttaja @navigaatio/valittu-urakka))

  TyhjennaAvattuToimenpide
  (process-event [_ app]
    (toimenpiteet/tyhjenna-avattu-toimenpide app))

  AsetaLomakkeenToimenpiteenTiedot
  (process-event [{toimenpide :toimenpide} app]
    (when (contains? (::kanavan-toimenpide/sijainti toimenpide) :virhe)
      (viesti/nayta! (get-in toimenpide [::kanavan-toimenpide/sijainti :virhe])
                     :danger))
    (toimenpiteet/aseta-lomakkeen-tiedot app toimenpide))

  ValinnatHaettuToimenpiteelle
  (process-event [{valinnat :valinnat} app]
    (merge app valinnat))

  VirheTapahtui
  (process-event [{virhe :virhe} app]
    (viesti/nayta! virhe :danger)
    app)

  HuoltokohteetHaettu
  (process-event [{huoltokohteet :huoltokohteet} app]
    (toimenpiteet/huoltokohteet-haettu app huoltokohteet))

  HuoltokohteidenHakuEpaonnistui
  (process-event [_ app]
    (toimenpiteet/huoltokohteet-ei-haettu app))

  TallennaToimenpide
  (process-event [{toimenpide :toimenpide poisto? :poisto?}
                  {valinnat :valinnat tehtavat :tehtavat :as app}]
    (toimenpiteet/tallenna-toimenpide app {:valinnat valinnat
                                           :tehtavat tehtavat
                                           :toimenpide toimenpide
                                           :tyyppi :kokonaishintainen
                                           :poisto? poisto?
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
              #(toimenpiteet/poista-valittu-toimenpide % (::kanavan-toimenpide/id toimenpide)))))

  KytkePaikannusKaynnissa
  (process-event [_ app]
    (update-in app [:avattu-toimenpide :paikannus-kaynnissa?] not))

  HaeMateriaalit
  (process-event [_ {:keys [materiaalien-haku-kaynnissa?] :as app}]
    (assert (some? materiaalien-haku-kaynnissa?) "huono tila: materiaalien-haku-kaynnissa? oli nil")
    (if-not materiaalien-haku-kaynnissa?
      (let [urakka-id (:id @navigaatio/valittu-urakka)]
        (-> app
            (tuck-apurit/post! :hae-vesivayla-materiaalilistaus
                               {::materiaalit/urakka-id urakka-id}
                               {:onnistui ->MateriaalitHaettu
                                :epaonnistui ->MateriaalienHakuEpaonnistui})
            (assoc :materiaalien-haku-kaynnissa? true
                   :urakan-materiaalit nil)))
      app))

  MateriaalitHaettu
  (process-event [{materiaalit :materiaalit} app]
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
