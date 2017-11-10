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
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka.toteumat.varusteet.viestit :as v]
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
              :sopimus-id (first @urakka/valittu-sopimusnumero)
              :aikavali @urakka/valittu-aikavali
              :toimenpide @urakka/valittu-toimenpideinstanssi})))

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
(defrecord TallennaToimenpide [toimenpide])
(defrecord ToimenpideTallennettu [toimenpide])
(defrecord ValinnatHaettuToimenpiteelle [valinnat])
(defrecord VirheTapahtui [virhe])

(defn hae-valinnat-toimenpiteelle [valinnat haku-valmis! virhe!]
  ;; todo: tee oikea palvelinkutsu (<! (vkm/tieosan-ajoradat (:numero uusi-tr) (:alkuosa uusi-tr)))
  (go (let [vastaus {:kohteet [{::kanavan-kohde/id 123
                                ::kanavan-kohde/nimi "Hilipati pippaa"}
                               {::kanavan-kohde/id 666
                                ::kanavan-kohde/nimi "Trolololo"}]
                     :toimenpidekoodit [{::toimenpidekoodi/id 666
                                         ::toimenpidekoodi/nimi "Hilipatus"}
                                        {::toimenpidekoodi/id 777
                                         ::toimenpidekoodi/nimi "Hapatus"}]
                     :huoltokohteet [{::kanavan-huoltokohde/nimi "Kampe"
                                      ::kanavan-huoltokohde/id 123}
                                     {::kanavan-huoltokohde/nimi "Hilavitkutin"
                                      ::kanavan-huoltokohde/id 666}]}]
        (if (k/virhe? vastaus)
          (virhe!)
          (haku-valmis! vastaus)))))

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
    (hae-valinnat-toimenpiteelle valinnat
                                 (t/send-async! ->ValinnatHaettuToimenpiteelle)
                                 (t/send-async! (partial ->VirheTapahtui "Valintojen hakeminen toimenpiteelle epäonnistui")))
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

  v/VirheTapahtui
  (process-event [{virhe :virhe} app]
    (viesti/nayta! virhe :danger)
    app))

