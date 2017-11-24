(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.id :refer [id-olemassa?]]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [harja.views.kanavat.urakka.toimenpiteet :as toimenpiteet-view]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.urakka :as urakka]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.tiedot.urakka :as urakkatiedot]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as navigaatio])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defrecord NakymaAvattu [])
(defrecord NakymaSuljettu [])
(defrecord PaivitaValinnat [valinnat])
(defrecord HaeToimenpiteet [valinnat])
(defrecord ToimenpiteetHaettu [toimenpiteet])
(defrecord ToimenpiteidenHakuEpaonnistui [])
(defrecord UusiToimenpide [])
(defrecord TyhjennaValittuToimenpide [])
(defrecord AsetaToimenpiteenTiedot [toimenpide])
(defrecord ValinnatHaettuToimenpiteelle [valinnat])
(defrecord VirheTapahtui [virhe])
(defrecord KohteetHaettu [kohteet])
(defrecord KohteidenHakuEpaonnistui [])
(defrecord HuoltokohteetHaettu [huoltokohteet])
(defrecord HuoltokohteidenHakuEpaonnistui [])
(defrecord TallennaToimenpide [toimenpide])
(defrecord ToimenpideTallennettu [toimenpiteet])
(defrecord ToimenpiteidenTallentaminenEpaonnistui [])
(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseToimenpiteet [tiedot])
(defrecord SiirraValitut [])
(defrecord ValitutSiirretty [])
(defrecord ValitutEiSiirretty [])
(defrecord PoistaToimenpide [toimenpide])

(def tila (atom {:nakymassa? false
                 :valinnat nil
                 :haku-kaynnissa? false
                 :toimenpiteiden-siirto-kaynnissa? false
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

(defn esitaytetty-toimenpide []
  (let [kayttaja @istunto/kayttaja]
    {::kanavan-toimenpide/sopimus-id (:paasopimus @navigaatio/valittu-urakka)
     ::kanavan-toimenpide/kuittaaja {::kayttaja/id (:id kayttaja)
                                     ::kayttaja/etunimi (:etunimi kayttaja)
                                     ::kayttaja/sukunimi (:sukunimi kayttaja)}}))

(defn tallennettava-toimenpide [tehtavat toimenpide]
  (let [tehtava (or (::kanavan-toimenpide/toimenpidekoodi-id toimenpide)
                    (get-in toimenpide [::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/id]))]
    (-> toimenpide
        (select-keys [::kanavan-toimenpide/id
                      ::kanavan-toimenpide/urakka-id
                      ::kanavan-toimenpide/suorittaja
                      ::kanavan-toimenpide/kuittaaja
                      ::kanavan-toimenpide/sopimus-id
                      ::kanavan-toimenpide/lisatieto
                      ::kanavan-toimenpide/toimenpideinstanssi-id
                      ::kanavan-toimenpide/toimenpidekoodi-id
                      ::kanavan-toimenpide/pvm
                      ::muokkaustiedot/poistettu?])
        (assoc ::kanavan-toimenpide/tyyppi :kokonaishintainen
               ::kanavan-toimenpide/kuittaaja-id (get-in toimenpide [::kanavan-toimenpide/kuittaaja ::kayttaja/id])
               ::kanavan-toimenpide/urakka-id (:id @navigaatio/valittu-urakka)
               ::kanavan-toimenpide/kohde-id (get-in toimenpide [::kanavan-toimenpide/kohde ::kanavan-kohde/id])
               ::kanavan-toimenpide/huoltokohde-id (get-in toimenpide [::kanavan-toimenpide/huoltokohde ::kanavan-huoltokohde/id])
               ::kanavan-toimenpide/muu-toimenpide (if (toimenpiteet/valittu-tehtava-muu? tehtava tehtavat)
                                                     (::kanavan-toimenpide/muu-toimenpide toimenpide)
                                                     nil))
        (dissoc ::kanavan-toimenpide/kuittaaja))))

(defn kokonashintaiset-tehtavat [tehtavat]
  (filter
    (fn [tehtava]
      (some #(= % "kokonaishintainen") (:hinnoittelu tehtava)))
    (map #(nth % 3) tehtavat)))

(extend-protocol tuck/Event
  NakymaAvattu
  (process-event [_ {:keys [kohteiden-haku-kaynnissa? huoltokohteiden-haku-kaynnissa?] :as app}]
    (if (or kohteiden-haku-kaynnissa? huoltokohteiden-haku-kaynnissa?)
      (assoc app :nakymassa? true)
      (let [aseta-valinnat! (tuck/send-async! ->PaivitaValinnat (alkuvalinnat))]
        (go (aseta-valinnat!))
        (-> app
            (tuck-apurit/post! :hae-urakan-kohteet
                               {::urakka/id (:id @navigaatio/valittu-urakka)}
                               {:onnistui ->KohteetHaettu
                                :epaonnistui ->KohteidenHakuEpaonnistui})
            (tuck-apurit/get! :hae-kanavien-huoltokohteet
                              {:onnistui ->HuoltokohteetHaettu
                               :epaonnistui ->HuoltokohteidenHakuEpaonnistui})
            (assoc :nakymassa? true
                   :kohteiden-haku-kaynnissa? true
                   :huoltokohteiden-haku-kaynnissa? true
                   :tehtavat (kokonashintaiset-tehtavat @urakkatiedot/urakan-toimenpiteet-ja-tehtavat)
                   :toimenpideinstanssit @urakkatiedot/urakan-toimenpideinstanssit
                   :kohteet []
                   :huoltokohteet [])))))

  NakymaSuljettu
  (process-event [_ app]
    (assoc app :nakymassa? false))

  PaivitaValinnat
  (process-event [{valinnat :valinnat} app]
    (let [haku (tuck/send-async! ->HaeToimenpiteet)]
      (go (haku valinnat))
      (assoc app :valinnat valinnat)))

  HaeToimenpiteet
  (process-event [{valinnat :valinnat} app]
    (if (and
          (get-in valinnat [:urakka :id])
          (not (:haku-kaynnissa? app)))
      (let [argumentit (toimenpiteet/muodosta-hakuargumentit valinnat :kokonaishintainen)]
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
    (when-not (:toimenpiteiden-siirto-kaynnissa? app)
      (-> app
          (tuck-apurit/post! :siirra-kanavatoimenpiteet
                             {::kanavan-toimenpide/toimenpide-idt (:valitut-toimenpide-idt app)
                              ::kanavan-toimenpide/urakka-id (get-in app [:valinnat :urakka :id])
                              ::kanavan-toimenpide/tyyppi :muutos-lisatyo}
                             {:onnistui ->ValitutSiirretty
                              :epaonnistui ->ValitutEiSiirretty})
          (assoc :toimenpiteiden-siirto-kaynnissa? true))))

  ValitutSiirretty
  (process-event [_ app]
    (viesti/nayta! (toimenpiteet-view/toimenpiteiden-toiminto-suoritettu
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
    (assoc app :valittu-toimenpide (esitaytetty-toimenpide)))

  TyhjennaValittuToimenpide
  (process-event [_ app]
    (dissoc app :valittu-toimenpide))

  AsetaToimenpiteenTiedot
  (process-event [{toimenpide :toimenpide} app]
    (assoc app :valittu-toimenpide toimenpide))

  ValinnatHaettuToimenpiteelle
  (process-event [{valinnat :valinnat} app]
    (merge app valinnat))

  VirheTapahtui
  (process-event [{virhe :virhe} app]
    (viesti/nayta! virhe :danger)
    app)

  KohteetHaettu
  (process-event [{kohteet :kohteet} app]
    (assoc app :kohteet kohteet
               :kohteiden-haku-kaynnissa? false))

  KohteidenHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Kohteiden haku epäonnistui" :danger)
    (assoc app :kohteiden-haku-kaynnissa? false))

  HuoltokohteetHaettu
  (process-event [{huoltokohteet :huoltokohteet} app]
    (assoc app :huoltokohteet huoltokohteet
               :huoltokohteiden-haku-kaynnissa? false))

  HuoltokohteidenHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Huoltokohteiden haku epäonnistui" :danger)
    (assoc app :huoltokohteiden-haku-kaynnissa? false))

  TallennaToimenpide
  (process-event [{data :toimenpide} {valinnat :valinnat tehtavat :tehtavat :as app}]
    app
    (if (:tallennus-kaynnissa? app)
      app
      (let [toimenpide (tallennettava-toimenpide tehtavat data)
            hakuehdot (toimenpiteet/muodosta-hakuargumentit valinnat :kokonaishintainen)]
        (-> app
            (tuck-apurit/post! :tallenna-kanavatoimenpide
                               {::kanavan-toimenpide/kanava-toimenpide toimenpide
                                ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}
                               {:onnistui ->ToimenpideTallennettu
                                :epaonnistui ->ToimenpiteidenTallentaminenEpaonnistui})
            (assoc :tallennus-kaynnissa? true)))))

  ToimenpideTallennettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (assoc app :tallennus-kaynnissa? false
               :valittu-toimenpide nil
               :toimenpiteet toimenpiteet))


  ToimenpiteidenTallentaminenEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden tallentaminen epäonnistui" :danger)
    (assoc app :tallennus-kaynnissa? false))

  PoistaToimenpide
  (process-event [{toimenpide :toimenpide} app]
    (let [tallennus! (tuck/send-async! ->TallennaToimenpide)]
      (go (tallennus! (assoc toimenpide ::muokkaustiedot/poistettu? true)))
      app)))
