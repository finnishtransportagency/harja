(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.urakka :as urakka]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.vesivaylat.materiaali :as materiaalit]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakkatiedot]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.istunto :as istunto]
            [harja.pvm :as pvm])
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
(defrecord MateriaalitHaettu [materiaalit])
(defrecord MateriaalienHakuEpaonnistui [])
;; Muokkaukset
(defrecord ValitseAjanTallennus [valittu?])
(defrecord AsetaHavaintoaika [aika])
(defrecord LisaaHairiotilanne [])
(defrecord TyhjennaValittuHairiotilanne [])
(defrecord AsetaHairiotilanteenTiedot [hairiotilanne])
(defrecord TallennaHairiotilanne [hairiotilanne])
(defrecord PoistaHairiotilanne [hairiotilanne])
(defrecord HairiotilanneTallennettu [tallennuksen-vastaus])
(defrecord HairiotilanteenTallentaminenEpaonnistui [])
(defrecord MuokkaaMateriaaleja [materiaalit])
(defrecord LisaaMateriaali [])
(defrecord LisaaVirhe [virhe])
(defrecord KytkePaikannusKaynnissa [])


(def valinnat
  (reaction
    (when (:nakymassa? @tila)
      {:urakka @nav/valittu-urakka
       :sopimus-id (first @urakkatiedot/valittu-sopimusnumero)
       :aikavali @urakkatiedot/valittu-aikavali})))

(defn esitaytetty-hairiotilanne []
  (let [kayttaja @istunto/kayttaja]
    {::hairiotilanne/tallennuksen-aika? true
     ::hairiotilanne/sopimus-id (:paasopimus @navigaatio/valittu-urakka)
     ::hairiotilanne/kuittaaja {::kayttaja/id (:id kayttaja)
                                ::kayttaja/etunimi (:etunimi kayttaja)
                                ::kayttaja/sukunimi (:sukunimi kayttaja)}
     ::hairiotilanne/havaintoaika (pvm/nyt)}))

(defn voi-tallentaa? [hairiotilanne]
  (and
    (or (::hairiotilanne/kohde hairiotilanne)
        (::hairiotilanne/sijainti hairiotilanne))
    (not (and (::hairiotilanne/kohde hairiotilanne)
              (::hairiotilanne/sijainti hairiotilanne)))))

(defn tallennettava-hairiotilanne [hairiotilanne]
  (let [hairiotilanne (-> hairiotilanne
                        ;; ;; Jos halutaan käyttää tallennushetken aikaa -> pvm/nyt
                        (assoc ::hairiotilanne/havaintoaika (if (::hairiotilanne/tallennuksen-aika? hairiotilanne)
                                           (pvm/nyt)
                                           (::hairiotilanne/havaintoaika hairiotilanne)))
                          (select-keys [::hairiotilanne/id
                                        ::hairiotilanne/sopimus-id
                                        ::hairiotilanne/paikallinen-kaytto?
                                        ::hairiotilanne/vikaluokka
                                        ::hairiotilanne/korjaustoimenpide
                                        ::hairiotilanne/korjauksen-tila
                                        ::hairiotilanne/huviliikenne-lkm
                                        ::hairiotilanne/korjausaika-h
                                        ::hairiotilanne/syy
                                        ::hairiotilanne/odotusaika-h
                                        ::hairiotilanne/ammattiliikenne-lkm
                                        ::muokkaustiedot/poistettu?
                                        ::hairiotilanne/havaintoaika
                                        ::hairiotilanne/sijainti])
                          (assoc ::hairiotilanne/urakka-id (:id @navigaatio/valittu-urakka)
                                 ::hairiotilanne/kohde-id (get-in hairiotilanne [::hairiotilanne/kohde ::kohde/id])
                                 ::hairiotilanne/kohteenosa-id (get-in hairiotilanne [::hairiotilanne/kohteenosa ::osa/id])))]
    hairiotilanne))

(defn tallennettava-materiaali [hairiotilanne]
  (let [materiaali-kirjaukset (::materiaalit/materiaalit hairiotilanne)
        muokkaamattomat-materiaali-kirjaukset (filter
                                                #(= (::materiaalit/pvm %) (::hairiotilanne/havaintoaika hairiotilanne))
                                                (::materiaalit/muokkaamattomat-materiaalit hairiotilanne))
        hairiotilanne-id (::hairiotilanne/id hairiotilanne)
        korjaustoimenpide (::hairiotilanne/korjaustoimenpide hairiotilanne)
        kohteen-nimi (get-in hairiotilanne [::hairiotilanne/kohde ::kohde/nimi])]
    (transduce
      (comp
        ;; Poistetaan muokkaamattomat materiaalit (muuten backin kantaan viennissä jää muokattu timestamp)
        (remove (fn [materiaalikirjaus]
                  (some #(= materiaalikirjaus %)
                        muokkaamattomat-materiaali-kirjaukset)))
        ;; poistetaan mapista poistetuksi merkatut rivit
        (remove :poistettu)
        ;; Poistetaan tyhjäksi jätetyt rivit
        (remove #(empty? (dissoc % :jarjestysnumero)))
        ;; Lisätään käytetty määrä lähetettävään mappiin ja
        ;; muutetaan miinusmerkkiseksi (muuten tulee merkattua lisäystä eikä käyttöä)
        (map #(assoc-in % [:tallennetut-materiaalit ::materiaalit/maara] (- (:maara %))))
        ;; Käsitellään pelkästään lähetettävää mappia
        (map :tallennetut-materiaalit)
        ;; Lisätään lisätieto ja materiaalin pvm, koska se on required field. Materiaalia
        ;; muokatessa kumminkin ei vaihdeta pvm:ää
        (map #(assoc % ::materiaalit/pvm (or (::hairiotilanne/havaintoaika hairiotilanne) (pvm/nyt))
                       ::materiaalit/lisatieto  (str "Käytetty kohteen " kohteen-nimi " häiriötilanteen korjaamiseen."
                                                     (when korjaustoimenpide (str " Korjaustoimenpide: " korjaustoimenpide)))))
        ;; Otetaan joitain vv_materiaalilistaus tietoja pois (muuten tulee herjaa palvelin päässä)
        (map #(dissoc %
                      ::materiaalit/maara-nyt
                      ::materiaalit/halytysraja
                      ::materiaalit/muutokset
                      ::materiaalit/alkuperainen-maara)))
      conj materiaali-kirjaukset)))

(defn poistettava-materiaali [hairiotilanne]
  (let [materiaali-kirjaukset (::materiaalit/materiaalit hairiotilanne)]
    (transduce
      (comp
        ;; poistetaan mapista poistetuksi merkatut uudet rivit
        (remove #(and (:poistettu %)
                      (:jarjestysnumero %)))
        ;; Käsitellään pelkästään poistetuksi merkattuja
        (filter :poistettu)
        ;; Käsitellään kannasta materiaalin poistaminen
        (map (fn [{materiaali :tallennetut-materiaalit}]
               (select-keys materiaali #{::materiaalit/id ::materiaalit/urakka-id}))))
      conj materiaali-kirjaukset)))

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
  (process-event [_ {:keys [materiaalien-haku-kaynnissa?] :as app}]
    (if materiaalien-haku-kaynnissa?
      (assoc app :nakymassa? true)
      (let [urakka-id (:id @navigaatio/valittu-urakka)]
        (-> app
            (tuck-apurit/post! :hae-vesivayla-materiaalilistaus
                               {::materiaalit/urakka-id urakka-id}
                               {:onnistui ->MateriaalitHaettu
                                :epaonnistui ->MateriaalienHakuEpaonnistui})
            (assoc :nakymassa? true
                   :materiaalien-haku-kaynnissa? true)))))

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
  
  ValitseAjanTallennus
  (process-event [{valittu? :valittu?} app]
    (assoc-in app [:valittu-hairiotilanne ::hairiotilanne/tallennuksen-aika?] (not valittu?)))
  
  AsetaHavaintoaika
  (process-event [{aika :aika} app]
    (assoc-in app [:valittu-hairiotilanne ::hairiotilanne/havaintoaika] aika))

  LisaaHairiotilanne
  (process-event [_ app]
    (assoc app :valittu-hairiotilanne (esitaytetty-hairiotilanne)))

  TyhjennaValittuHairiotilanne
  (process-event [_ app]
    (dissoc app :valittu-hairiotilanne))

  AsetaHairiotilanteenTiedot
  (process-event [{hairiotilanne :hairiotilanne} app]
    (let [kohdeosa-vaihtui? (and (some? (get-in app [:valittu-hairiotilanne ::hairiotilanne/kohteenosa]))
                                 (not= (::hairiotilanne/kohde hairiotilanne)
                                       (get-in app [:valittu-hairiotilanne ::hairiotilanne/kohde])))
          hairiotilanne (if kohdeosa-vaihtui?
                          (assoc hairiotilanne ::hairiotilanne/kohteenosa nil)
                          hairiotilanne)]
      (assoc app :valittu-hairiotilanne hairiotilanne)))

  TallennaHairiotilanne
  (process-event [{hairiotilanne :hairiotilanne} {valinnat :valinnat :as app}]
    (if (:tallennus-kaynnissa? app)
      app
      (let [tal-hairiotilanne (tallennettava-hairiotilanne hairiotilanne)
            tal-materiaalit (tallennettava-materiaali hairiotilanne)
            pois-materiaalit (poistettava-materiaali hairiotilanne)
            parametrit (hairiotilanteiden-hakuparametrit valinnat)]
        (-> app
            (tuck-apurit/post! :tallenna-hairiotilanne
                               {::hairiotilanne/hairiotilanne tal-hairiotilanne
                                ::materiaalit/materiaalikirjaukset tal-materiaalit
                                ::materiaalit/poista-materiaalikirjauksia pois-materiaalit
                                ::hairiotilanne/hae-hairiotilanteet-kysely parametrit}
                               {:onnistui ->HairiotilanneTallennettu
                                :epaonnistui ->HairiotilanteenTallentaminenEpaonnistui})
            (assoc :tallennus-kaynnissa? true)))))

  PoistaHairiotilanne
  (process-event [{hairiotilanne :hairiotilanne} app]
    (let [tallennus! (tuck/send-async! ->TallennaHairiotilanne)]
      (go (tallennus! (assoc hairiotilanne ::muokkaustiedot/poistettu? true
                                           ::materiaalit/materiaalit (map #(assoc % :poistettu true)
                                                                          (::materiaalit/materiaalit hairiotilanne)))))
      app))

  MateriaalitHaettu
  (process-event [{materiaalit :materiaalit} app]
    (assoc app :materiaalit materiaalit
               :materiaalien-haku-kaynnissa? false))

  MateriaalienHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Materiaalien haku epäonnistui" :danger)
    (assoc app :materiaalien-haku-kaynnissa? false))

  HairiotilanneTallennettu
  (process-event [{tallennuksen-vastaus :tallennuksen-vastaus} app]
    (let [{hairiotilanteet :hairiotilanteet
           materiaalilistaukset :materiaalilistaukset} tallennuksen-vastaus]
      (assoc app :tallennus-kaynnissa? false
                 :valittu-hairiotilanne nil
                 :hairiotilanteet hairiotilanteet
                 :materiaalit materiaalilistaukset)))

  HairiotilanteenTallentaminenEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Häiriotilanteen tallennus epäonnistui" :danger)
    (assoc app :tallennus-kaynnissa? false))

  ValitseHairiotilanne
  (process-event [{hairiotilanne :hairiotilanne} {:keys [materiaalit] :as app}]
    ;;hairiotilanteiden mukana voisi kannasta tuoda myös häiriötilanteiden materiaalit
    ;;mutta ne täytyisi joka tapauksessa formatoida materiaalit gridille sopivaan muotoon,
    ;;niin sama formatoida jo haetuista materiaalilistauksista.
    (let [materiaali-kirjaukset (mapcat (fn [materiaalilistaus]
                                          (transduce
                                            (comp
                                              ;; Filtteröidään ensin hairiotilanteelle kuuluvat
                                              ;; materiaalikirjaukset
                                              (filter #(= (::hairiotilanne/id hairiotilanne)
                                                          (::materiaalit/hairiotilanne %)))
                                              ;; Materiaalit gridissä on :maara, :yksikko ja :tallennetut-materiaalit nimiset sarakkeet. Materiaalin
                                              ;; nimi, urakka-id, pvm ja id tarvitaan tallentamista varten.
                                              (map #(identity {:maara (- (::materiaalit/maara %))
                                                               :yksikko (::materiaalit/yksikko materiaalilistaus)
                                                               :tallennetut-materiaalit {::materiaalit/nimi (::materiaalit/nimi materiaalilistaus)
                                                                         ::materiaalit/urakka-id (::materiaalit/urakka-id materiaalilistaus)
                                                                         ::materiaalit/pvm (::materiaalit/pvm %)
                                                                         ::materiaalit/id (::materiaalit/id %)
                                                                         ::materiaalit/yksikko (::materiaalit/yksikko materiaalilistaus)}})))
                                            conj (::materiaalit/muutokset materiaalilistaus)))
                                        materiaalit)]
      (-> app
          (assoc :valittu-hairiotilanne hairiotilanne)
          (assoc-in [:valittu-hairiotilanne ::materiaalit/materiaalit] materiaali-kirjaukset)
          (assoc-in [:valittu-hairiotilanne ::materiaalit/muokkaamattomat-materiaalit] materiaali-kirjaukset))))

  MuokkaaMateriaaleja
  (process-event [{materiaalit :materiaalit} app]
    (if (:valittu-hairiotilanne app)
      (update app :valittu-hairiotilanne #(assoc % ::materiaalit/materiaalit materiaalit))
      app))

  LisaaMateriaali
  (process-event [_ app]
    ;; Materiaalien järjestystä varten täytyy käyttää järjestysnumeroa. Nyt ei voida käyttää muokkaus-gridin generoimaa
    ;; numeroa, koska rivinlisäysnappi ei ole normaali gridin lisäysnappi
    (update-in app
               [:valittu-hairiotilanne ::materiaalit/materiaalit]
               #(let [vanha-id (apply max (map :jarjestysnumero %))
                      uusi-id (if (nil? vanha-id) 0 (inc vanha-id))]
                  (conj (vec %) {:jarjestysnumero uusi-id}))))

  LisaaVirhe
  (process-event [{virhe :virhe} app]
    (assoc-in app [:valittu-hairiotilanne :materiaalit-taulukon-virheet] virhe))

  KytkePaikannusKaynnissa
  (process-event [_ app]
    (update-in app [:valittu-hairiotilanne :paikannus-kaynnissa?] not)))
