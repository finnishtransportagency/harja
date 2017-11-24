(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.urakka :as urakka]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kanavan-kohde :as kanavan-kohde]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.vesivaylat.materiaali :as materiaalit]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakkatiedot]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :hairiotilanteet nil
                 :hairiotilanteiden-haku-kaynnissa? false
                 :valinnat nil}))

;; Yleiset
(defrecord NakymaAvattu [])
(defrecord NakymaSuljettu [])
(defrecord PaivitaValinnat [valinnat])
(defrecord ValitseHairiotilanne [hairiotilanne])
;; Haut
(defrecord HaeHairiotilanteet [valinnat])
(defrecord HairiotilanteetHaettu [tulos])
(defrecord HairiotilanteetEiHaettu [])
(defrecord KohteetHaettu [kohteet])
(defrecord KohteidenHakuEpaonnistui [])
(defrecord MateriaalitHaettu [materiaalit])
(defrecord MateriaalienHakuEpaonnistui [])
;; Muokkaukset
(defrecord LisaaHairiotilanne [])
(defrecord TyhjennaValittuHairiotilanne [])
(defrecord AsetaHairiotilanteenTiedot [hairiotilanne])
(defrecord TallennaHairiotilanne [hairiotilanne])
(defrecord PoistaHairiotilanne [hairiotilanne])
(defrecord HairiotilanneTallennettu [hairiotilanteet])
(defrecord HairiotilanteenTallentaminenEpaonnistui [])
(defrecord MuokkaaMateriaaleja [materiaalit])


(def valinnat
  (reaction
    (when (:nakymassa? @tila)
      {:urakka @nav/valittu-urakka
       :sopimus-id (first @urakkatiedot/valittu-sopimusnumero)
       :aikavali @urakkatiedot/valittu-aikavali})))

(defn esitaytetty-hairiotilanne []
  (let [kayttaja @istunto/kayttaja]
    {::hairiotilanne/sopimus-id (:paasopimus @navigaatio/valittu-urakka)
     ::hairiotilanne/kuittaaja {::kayttaja/id (:id kayttaja)
                                ::kayttaja/etunimi (:etunimi kayttaja)
                                ::kayttaja/sukunimi (:sukunimi kayttaja)}}))

(defn tallennettava-hairiotilanne [hairiotilanne]
  (let [hairiotilanne (-> hairiotilanne
                          (select-keys [::hairiotilanne/id
                                        ::hairiotilanne/sopimus-id
                                        ::hairiotilanne/paikallinen-kaytto?
                                        ::hairiotilanne/vikaluokka
                                        ::hairiotilanne/korjaustoimenpide
                                        ::hairiotilanne/korjauksen-tila
                                        ::hairiotilanne/pvm
                                        ::hairiotilanne/huviliikenne-lkm
                                        ::hairiotilanne/korjausaika-h
                                        ::hairiotilanne/syy
                                        ::hairiotilanne/odotusaika-h
                                        ::hairiotilanne/ammattiliikenne-lkm
                                        ::hairiotilanne/materiaalit materiaalit
                                        ::muokkaustiedot/poistettu?])
                          (assoc ::hairiotilanne/kuittaaja-id (get-in hairiotilanne [::hairiotilanne/kuittaaja ::kayttaja/id])
                                 ::hairiotilanne/urakka-id (:id @navigaatio/valittu-urakka)
                                 ::hairiotilanne/kohde-id (get-in hairiotilanne [::hairiotilanne/kohde ::kanavan-kohde/id])))]
    hairiotilanne))

(defn hairiotilanteiden-hakuparametrit [valinnat]
  {::hairiotilanne/urakka-id (get-in valinnat [:urakka :id])
   :haku-sopimus-id (:sopimus-id valinnat)
   :haku-vikaluokka (:vikaluokka valinnat)
   :haku-korjauksen-tila (:korjauksen-tila valinnat)
   :haku-odotusaika-h (:odotusaika-h valinnat)
   :haku-korjausaika-h (:korjausaika-h valinnat)
   :haku-paikallinen-kaytto? (:paikallinen-kaytto? valinnat)
   :haku-aikavali (:aikavali valinnat)})

(extend-protocol tuck/Event
  NakymaAvattu
  (process-event [_ {:keys [kohteiden-haku-kaynnissa? materiaalien-haku-kaynnissa?] :as app}]
    (if (or kohteiden-haku-kaynnissa? materiaalien-haku-kaynnissa?)
      (assoc app :nakymassa? true)
      (let [urakka-id (:id @navigaatio/valittu-urakka)]
        (-> app
            (tuck-apurit/post! :hae-urakan-kohteet
                               {::urakka/id urakka-id}
                               {:onnistui ->KohteetHaettu
                                :epaonnistui ->KohteidenHakuEpaonnistui})
            (tuck-apurit/post! :hae-vesivayla-materiaalilistaus
                               {::materiaalit/urakka-id urakka-id}
                               {:onnistui ->MateriaalitHaettu
                                :epaonnistui ->MateriaalienHakuEpaonnistui})
            (assoc :nakymassa? true
                   :kohteiden-haku-kaynnissa? true
                   :materiaalien-haku-kaynnissa? true
                   :kohteet []
                   :materiaalit [])))))

  NakymaSuljettu
  (process-event [_ app]
    (assoc app :nakymassa? false))

  PaivitaValinnat
  (process-event [{val :valinnat} app]
    (let [uudet-valinnat (merge (:valinnat app) val)
          haku (tuck/send-async! ->HaeHairiotilanteet)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  HaeHairiotilanteet
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:hairiotilanteiden-haku-kaynnissa? app))
             (some? (get-in valinnat [:urakka :id])))
      (let [argumentit (hairiotilanteiden-hakuparametrit valinnat)]
        (-> app
            (tuck-apurit/post! :hae-hairiotilanteet
                               argumentit
                               {:onnistui ->HairiotilanteetHaettu
                                :epaonnistui ->HairiotilanteetEiHaettu})
            (assoc :hairiotilanteiden-haku-kaynnissa? true)))
      app))

  HairiotilanteetHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :hairiotilanteiden-haku-kaynnissa? false
               :hairiotilanteet tulos))

  HairiotilanteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Häiriötilanteiden haku epäonnistui!" :danger)
    (assoc app :hairiotilanteiden-haku-kaynnissa? false
               :hairiotilanteet []))

  LisaaHairiotilanne
  (process-event [_ app]
    (assoc app :valittu-hairiotilanne (esitaytetty-hairiotilanne)))

  TyhjennaValittuHairiotilanne
  (process-event [_ app]
    (dissoc app :valittu-hairiotilanne))

  AsetaHairiotilanteenTiedot
  (process-event [{hairiotilanne :hairiotilanne} app]
    (assoc app :valittu-hairiotilanne hairiotilanne))

  TallennaHairiotilanne
  (process-event [{hairiotilanne :hairiotilanne} {valinnat :valinnat :as app}]

    (if (:tallennus-kaynnissa? app)
      app
      (let [hairiotilanne (tallennettava-hairiotilanne hairiotilanne)
            parametrit (hairiotilanteiden-hakuparametrit valinnat)]
        (-> app
            (tuck-apurit/post! :tallenna-hairiotilanne
                               {::hairiotilanne/hairiotilanne hairiotilanne
                                ::hairiotilanne/hae-hairiotilanteet-kysely parametrit}
                               {:onnistui ->HairiotilanneTallennettu
                                :epaonnistui ->HairiotilanteenTallentaminenEpaonnistui})
            (assoc :tallennus-kaynnissa? true))))

    (assoc app :tallennus-kaynnissa? true))

  PoistaHairiotilanne
  (process-event [{hairiotilanne :hairiotilanne} app]
    (let [tallennus! (tuck/send-async! ->TallennaHairiotilanne)]
      (go (tallennus! (assoc hairiotilanne ::muokkaustiedot/poistettu? true)))
      app))

  KohteetHaettu
  (process-event [{kohteet :kohteet} app]
    (assoc app :kohteet kohteet
               :kohteiden-haku-kaynnissa? false))

  KohteidenHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Kohteiden haku epäonnistui" :danger)
    (assoc app :kohteiden-haku-kaynnissa? false))

  MateriaalitHaettu
  (process-event [{materiaalit :materiaalit} app]
    (assoc app :materiaalit materiaalit
               :materiaalien-haku-kaynnissa? false))

  MateriaalienHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Materiaalien haku epäonnistui" :danger)
    (assoc app :materiaalien-haku-kaynnissa? false))

  HairiotilanneTallennettu
  (process-event [{hairiotilanteet :hairiotilanteet} app]
    (assoc app :tallennus-kaynnissa? false
               :valittu-hairiotilanne nil
               :hairiotilanteet hairiotilanteet))

  HairiotilanteenTallentaminenEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Häiriotilanteen tallennus epäonnistui" :danger)
    (assoc app :tallennus-kaynnissa? false))

  ValitseHairiotilanne
  (process-event [{hairiotilanne :hairiotilanne} app]
    (assoc app :valittu-hairiotilanne hairiotilanne))

  MuokkaaMateriaaleja
  (process-event [{materiaalit :materiaalit} app]
    (if (:valittu-hairiotilanne app)
      (assoc app :valittu-hairiotilanne
                 (assoc (:valittu-hairiotilanne app) ::hairiotilanne/materiaalit materiaalit))
      app)))

