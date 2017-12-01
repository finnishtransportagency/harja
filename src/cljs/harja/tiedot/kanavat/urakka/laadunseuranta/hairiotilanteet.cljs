(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.urakka :as urakka]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kohde :as kohde]
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
(defrecord HairiotilanneTallennettu [tallennuksen-vastaus])
(defrecord HairiotilanteenTallentaminenEpaonnistui [])
(defrecord MuokkaaMateriaaleja [materiaalit])
(defrecord LisaaMateriaali [])


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
  (let [paivamaara (:paivamaara hairiotilanne)
        aika (:aika hairiotilanne)
        hairiotilanne (-> hairiotilanne
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
                                        ::muokkaustiedot/poistettu?])
                          (assoc ::hairiotilanne/kuittaaja-id (get-in hairiotilanne [::hairiotilanne/kuittaaja ::kayttaja/id])
                                 ::hairiotilanne/urakka-id (:id @navigaatio/valittu-urakka)
                                 ::hairiotilanne/kohde-id (get-in hairiotilanne [::hairiotilanne/kohde ::kohde/id])
                                 ::hairiotilanne/pvm (pvm/yhdista-pvm-ja-aika paivamaara aika)))]
    hairiotilanne))

(defn tallennettava-materiaali [hairiotilanne]
  (let [materiaali-kirjaukset (::materiaalit/materiaalit hairiotilanne)
        hairiotilanne-id (::hairiotilanne/id hairiotilanne)
        paivamaara (::hairiotilanne/pvm hairiotilanne)
        kohteen-nimi (get-in hairiotilanne [::hairiotilanne/kohde ::kohde/nimi])]
    (transduce
      (comp
        ;; ensin poistetaan mapista poistetuksi merkatut rivit
        (remove #(:poistettu %))
        ;; Lisätään käytetty määrä lähetettävään mappiin ja
        ;; muutetaan miinusmerkkiseksi (muuten tulee merkattua lisäystä eikä käyttöä)
        (map #(assoc-in % [:varaosa ::materiaalit/maara] (- (:maara %))))
        ;; Käsitellään pelkästään lähetettävää mappia
        (map :varaosa)
        ;; Lisätään muokkauspäivämäärää ja häiriö-id
        (map #(assoc % ::materiaalit/pvm (or (::materiaalit/pvm %) (pvm/nyt))
                       ::materiaalit/hairiotilanne hairiotilanne-id
                       ::materiaalit/lisatieto (str "Käytetty häiriötilanteessa " (pvm/pvm paivamaara)
                                                    " kohteessa " kohteen-nimi)))
        ;; Otetaan joitain vv_materiaalilistaus tietoja pois (muuten tulee herjaa palvelin päässä)
        (map #(dissoc %
                      ::materiaalit/maara-nyt
                      ::materiaalit/halytysraja
                      ::materiaalit/muutokset
                      ::materiaalit/alkuperainen-maara)))
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
      (let [tal-hairiotilanne (tallennettava-hairiotilanne hairiotilanne)
            tal-materiaalit (tallennettava-materiaali hairiotilanne)
            parametrit (hairiotilanteiden-hakuparametrit valinnat)]
        (-> app
            (tuck-apurit/post! :tallenna-hairiotilanne
                               {::hairiotilanne/hairiotilanne tal-hairiotilanne
                                ::materiaalit/materiaalikirjaukset tal-materiaalit
                                ::hairiotilanne/hae-hairiotilanteet-kysely parametrit}
                               {:onnistui ->HairiotilanneTallennettu
                                :epaonnistui ->HairiotilanteenTallentaminenEpaonnistui})
            (assoc :tallennus-kaynnissa? true)))))

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
    ;;mutta ne täytyisi joka tapauksessa formatoida varaosat gridille sopivaan muotoon,
    ;;niin sama formatoida jo haetuista materiaalilistauksista.
    (let [materiaali-kirjaukset (mapcat (fn [materiaalilistaus]
                                       (transduce
                                         (comp
                                           ;; Filtteröidään ensin hairiotilanteelle kuuluvat
                                           ;; materiaalikirjaukset
                                           (filter #(= (::hairiotilanne/id hairiotilanne)
                                                       (::materiaalit/hairiotilanne %)))
                                           ;; Varaosat gridissä on :maara ja :varaosa nimiset sarakkeet. Materiaalin
                                           ;; nimi, urakka-id, pvm ja id tarvitaan tallentamista varten.
                                           (map #(identity {:maara (- (::materiaalit/maara %))
                                                            :varaosa {::materiaalit/nimi (::materiaalit/nimi materiaalilistaus)
                                                                      ::materiaalit/urakka-id (::materiaalit/urakka-id materiaalilistaus)
                                                                      ::materiaalit/pvm (::materiaalit/pvm %)
                                                                      ::materiaalit/id (::materiaalit/id %)}})))
                                         conj (::materiaalit/muutokset materiaalilistaus)))
                                     materiaalit)
          paivamaara-ja-aika (pvm/DateTime->pvm-ja-aika (::hairiotilanne/pvm hairiotilanne))
          keskenerainen (str (get-in paivamaara-ja-aika [:aika :tunnit]) ":"
                             (get-in paivamaara-ja-aika [:aika :minuutit]))]
      (-> app
          (assoc :valittu-hairiotilanne hairiotilanne)
          (assoc-in [:valittu-hairiotilanne :paivamaara] (:pvm paivamaara-ja-aika))
          (assoc-in [:valittu-hairiotilanne :aika] (pvm/map->Aika (merge (:aika paivamaara-ja-aika) {:keskenerainen keskenerainen})))
          (assoc-in [:valittu-hairiotilanne ::materiaalit/materiaalit] materiaali-kirjaukset))))

  MuokkaaMateriaaleja
  (process-event [{materiaalit :materiaalit} app]
    (if (:valittu-hairiotilanne app)
      (update app :valittu-hairiotilanne #(assoc % ::materiaalit/materiaalit materiaalit))
      app))

  LisaaMateriaali
  (process-event [_ app]
    (update-in app [:valittu-hairiotilanne ::materiaalit/materiaalit] #(conj (vec %) {:foo (- (rand-int 100))}))))

