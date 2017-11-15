(ns harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :as async]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as toimenpiteet]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.urakka :as urakka]
            [harja.tiedot.urakka :as urakkatiedot]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as navigaatio]
            [tuck.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :valinnat nil
                 :haku-kaynnissa? false
                 :toimenpiteet nil}))

(defonce valinnat
         (reaction
           (when (:nakymassa? @tila)
             {:urakka @navigaatio/valittu-urakka
              :sopimus-id (first @urakkatiedot/valittu-sopimusnumero)
              :aikavali @urakkatiedot/valittu-aikavali
              :toimenpide @urakkatiedot/valittu-toimenpideinstanssi})))

(defn esitaytetty-toimenpide []
  (let [kayttaja @istunto/kayttaja]
    {::kanavan-toimenpide/sopimus-id (:paasopimus @navigaatio/valittu-urakka)
     ::kanavan-toimenpide/kuittaaja {::kayttaja/id (:id kayttaja)
                                     ::kayttaja/etunimi (:etunimi kayttaja)
                                     ::kayttaja/sukunimi (:sukunimi kayttaja)}}))

(defrecord Nakymassa? [nakymassa?])
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

(defn tallennettava-toimenpide [toimenpide]
  (-> toimenpide
      (select-keys [::kanavan-toimenpide/suorittaja
                    ::kanavan-toimenpide/kuittaaja
                    ::kanavan-toimenpide/muu-toimenpide
                    ::kanavan-toimenpide/sopimus-id
                    ::kanavan-toimenpide/lisatieto
                    ::kanavan-toimenpide/kohde-id
                    ::kanavan-toimenpide/toimenpidekoodi-id
                    ::kanavan-toimenpide/pvm
                    ::kanavan-toimenpide/huoltokohde-id])
      (assoc ::kanavan-toimenpide/kuittaaja-id (get-in toimenpide [::kanavan-toimenpide/kuittaaja ::kayttaja/id])
             ::kanavan-toimenpide/tyyppi :kokonaishintainen
             ::kanavan-toimenpide/urakka-id (:id @navigaatio/valittu-urakka))
      (dissoc ::kanavan-toimenpide/kuittaaja)))

(defn kokonashintaiset-tehtavat [tehtavat]
  (filter
    (fn [tehtava]
      (some #(= % "kokonaishintainen") (:hinnoittelu tehtava)))
    (map #(nth % 3) tehtavat)))

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

  UusiToimenpide
  (process-event [_ {valinnat :valinnat :as app}]
    (if (or (:kohteiden-haku-kaynnissa? app)
            (:huoltokohteiden-haku-kaynnissa? app))
      app
      (-> app
          (tuck-apurit/post! :hae-urakan-kohteet
                             {::urakka/id (get-in valinnat [:urakka :id])}
                             {:onnistui ->KohteetHaettu
                              :epaonnistui ->KohteidenHakuEpaonnistui})
          (tuck-apurit/get! :hae-kanavien-huoltokohteet
                            {:onnistui ->HuoltokohteetHaettu
                             :epaonnistui ->HuoltokohteidenHakuEpaonnistui})
          (assoc :huoltokohteiden-haku-kaynnissa? true
                 :valittu-toimenpide (esitaytetty-toimenpide)
                 :tehtavat (kokonashintaiset-tehtavat @urakkatiedot/urakan-toimenpiteet-ja-tehtavat)
                 :toimenpideinstanssit @urakkatiedot/urakan-toimenpideinstanssit
                 :kohteet []
                 :huoltokohteet []))))

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
  (process-event [{data :toimenpide} {valinnat :valinnat :as app}]
    app
    (if (:tallennus-kaynnissa? app)
      app
      (let [toimenpide (tallennettava-toimenpide data)
            hakuehdot (toimenpiteet/muodosta-hakuargumentit valinnat :kokonaishintainen)]
        (-> app
            (tuck-apurit/post! :tallenna-kanavatoimenpide
                               {::kanavan-toimenpide/kanava-toimenpide toimenpide
                                ::kanavan-toimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}
                               {:onnistui ->ToimenpideTallennettu
                                :epaonnistui ->ToimenpiteidenTallentaminenEpaonnistui})
            (assoc :tallennus-kaynnissa? true)))))

  ToimenpideTallennettu
  (process-event [{toimenpiteet :toimenpide} app]
    app)

  ToimenpiteidenTallentaminenEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden tallentaminen epäonnistui" :danger)
    (assoc app :tallennus-kaynnissa? false)))