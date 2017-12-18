(ns harja.tiedot.kanavat.urakka.toimenpiteet
  "Kanavatoimenpiteiden yhteiset asiat"
  (:require [reagent.core :refer [atom]]
            [harja.id :refer [id-olemassa?]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavatoimenpide]
            [clojure.string :as str]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.domain.vesivaylat.materiaali :as materiaalit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn muodosta-kohteiden-hakuargumentit [valinnat tyyppi]
  {::kanavatoimenpide/urakka-id (:id (:urakka valinnat))
   ::kanavatoimenpide/sopimus-id (:sopimus-id valinnat)
   ::toimenpidekoodi/id (get-in valinnat [:toimenpide :id])
   ::kanavatoimenpide/kanava-toimenpidetyyppi tyyppi
   :alkupvm (first (:aikavali valinnat))
   :loppupvm (second (:aikavali valinnat))
   ::kanavatoimenpide/kohde-id (:kanava-kohde-id valinnat)})

(defn esitaytetty-toimenpidelomake [kayttaja urakka]
  {::kanavatoimenpide/sopimus-id (:paasopimus urakka)
   ::kanavatoimenpide/kuittaaja {::kayttaja/id (:id kayttaja)
                                   ::kayttaja/etunimi (:etunimi kayttaja)
                                   ::kayttaja/sukunimi (:sukunimi kayttaja)}})

(defn uusi-toimenpide [app kayttaja urakka]
  (assoc app :avattu-toimenpide (esitaytetty-toimenpidelomake kayttaja urakka)))

(defn valittu-tehtava-muu? [tehtava-id tehtavat]
  (and
    tehtavat
    (some #(= % tehtava-id)
          (map :id
               (filter #(and
                          (:nimi %)
                          (not= -1 (.indexOf (str/upper-case (:nimi %)) "MUU"))) tehtavat)))))

(defn tyhjenna-avattu-toimenpide [app]
  (assoc app :avattu-toimenpide nil))

(defn materiaalilistaus->grid [toimenpide listaukset]
  (mapcat (fn [materiaalilistaus]
            (transduce
             (comp
              ;; Varaosat gridissä on :maara ja :varaosa nimiset sarakkeet. Materiaalin
              ;; nimi, urakka-id, pvm ja id tarvitaan tallentamista varten.
              (map #(identity {:maara (- (::materiaalit/maara %))
                               :varaosa {::materiaalit/nimi (::materiaalit/nimi materiaalilistaus)
                                         ::materiaalit/urakka-id (::materiaalit/urakka-id materiaalilistaus)
                                         ::materiaalit/pvm (::materiaalit/pvm %)
                                         ::materiaalit/id (::materiaalit/id %)}})))
             conj (filter
                   #(= (::materiaalit/toimenpide %) (::kanavatoimenpide/id toimenpide))
                   (::materiaalit/muutokset materiaalilistaus))))
          listaukset))

(defn aseta-lomakkeen-tiedot [app toimenpide]
  ;; (log "alt: app" (pr-str app) " tp " (pr-str toimenpide))
  (let [kohdeosa-vaihtui? (and (some? (get-in app [:avattu-toimenpide ::kanavatoimenpide/kohteenosa]))
                               (not= (::kanavatoimenpide/kohde toimenpide)
                                     (get-in app [:avattu-toimenpide ::kanavatoimenpide/kohde])))
        toimenpide (if kohdeosa-vaihtui?
                     (assoc toimenpide ::kanavatoimenpide/kohteenosa nil)
                     toimenpide)
        materiaalilistaukset (:urakan-materiaalit app)
        ;; todo: ei toimi ihan näin, urakan-materiaalit on "materiaalilistaus" eli rivi per tyyppi,
        ;;  pitää kaivaa sen muutoksista

        materiaalit (materiaalilistaus->grid toimenpide materiaalilistaukset)
        toimenpide (assoc toimenpide ::materiaalit/materiaalit materiaalit
                             ::materiaalit/muokkaamattomat-materiaalit materiaalit)]
    (log "alt: lopullinen materiaalit:")
    (cljs.pprint/pprint materiaalit)
    (assoc app :avattu-toimenpide toimenpide)))


;; materiaalien tietomallissa backilla:
;; vv_materiaalilistaus on view, sisältää rivin per materiaali (esim Naulat) ja maara-nyt arvon +muutokset-arrayn.
;; sisältö tehdään vv_materiaali-taulusta.
;; vv_materiaali aka materiaalikirjaus on yhtä muutosta esittävä rivi.
;; front-koodissa taas materiaalikirjauksiksi kutsutaan myös grid-mappeina olevia kirjauksiksi.
;;
;; ::materiaalit/materiaalit -avaimen alla oleva map sisaltaa :varaosa avaimen alla listaus-viewin muotoa,
;; {:jarjestysnumero 0,
;;  :varaosa {::materiaalit/urakka-id 31,
;;            ::materiaalit/maara-nyt 964,
;;            ::materiaalit/halytysraja 200,
;;            ::materiaalit/muutokset [
;;                                     [...]
;;                                     {::materiaalit/pvm #object[Object 20171128T000000],
;;                                      ::materiaalit/maara -3,
;;                                      ::materiaalit/toimenpide 2,
;;                                      ::materiaalit/id 5}
;;                                     [...]
;;                                     ],
;;            ::materiaalit/alkuperainen-maara 1000,
;;            ::materiaalit/nimi "Naulat"},
;;  :maara 2}

(defn materiaalikirjaus->tallennettava [grid-rivi]
  (log "mk->t..")

  (for [muutos (::materiaalit/muutokset grid-rivi)]
    (do (log "mk->t: muutos" (pr-str muutos))
        {:maara (- (::materiaalit/maara muutos))
         :varaosa {::materiaalit/nimi (::materiaalit/nimi grid-rivi)
                   ::materiaalit/urakka-id (::materiaalit/urakka-id grid-rivi)
                   ::materiaalit/pvm (::materiaalit/pvm muutos)
                   ::materiaalit/id (::materiaalit/id muutos)}})))

(defn materiaalikirjaus->poistettavat [{:keys [poistettu jarjestysnumero varaosa] :as grid-rivi}]
  ;; poistetaan mapista poistetuksi merkatut uudet rivit
  (when poistettu
    (when (not jarjestysnumero)
      (select-keys varaosa #{::materiaalit/id ::materiaalit/urakka-id}))))

(defn poistettavat-materiaalit [tp]
  (log "poistettavat" (pr-str (keep materiaalikirjaus->poistettavat (::materiaalit/materiaalit tp))))
  (keep materiaalikirjaus->poistettavat (::materiaalit/materiaalit tp)))

(defn yksi-tallennettava-materiaalikirjaus [muokkaamattomat-kirjaukset lisatieto m-kirjaus]
  "Palauttaa tallennettavan mapin kun saadaan annetaan muokattu, ei-tyhjat, ei-poistettu grid-rivi tyyliin {:varaosa ... :maara ...}"
  (let [muokattu? (first (filter (partial = m-kirjaus) muokkaamattomat-kirjaukset))
        tyhja? (= [:jarjestysnumero] (keys m-kirjaus))
        poistettu? (:poistettu m-kirjaus)
        varaosa (dissoc (:varaosa m-kirjaus)
                        ::materiaalit/maara-nyt ::materiaalit/halytysraja
                        ::materiaalit/muutokset ::materiaalit/alkuperainen-maara)]
    ;; (log "muokkaamattomat" (pr-str muokkaamattomat-kirjaukset))
    ;; (log "m-kirjaus" (pr-str m-kirjaus))
    ;; (log "yksi-tallennettava: tilat " (pr-str [tyhja? poistettu? (not muokattu?)]))
    (when-not (or tyhja? poistettu? (not muokattu?))
      ;; muutetaan miinusmerkkiseksi (muuten tulee merkattua lisäystä eikä käyttöä)
      (assoc varaosa
             ::materiaalit/maara (- (:maara m-kirjaus))
             ::materiaalit/lisätieto (or lisatieto "Käytetty toimenpiteen kirjauksesssa")))))

(defn tallennettavat-materiaalit [tp]
  (let [materiaali-kirjaukset (::materiaalit/materiaalit tp)
        muokkaamattomat-materiaali-kirjaukset (::materiaalit/muokkaamattomat-materiaalit tp)

        tp-id (::kanavatoimenpide/id tp)
        paivamaara (::kanavatoimenpide/pvm tp)
        kohteen-nimi (-> tp ::kanavatoimenpide/huoltokohde ::kanavan-huoltokohde/nimi)

        lisatieto (str "Kohteen " kohteen-nimi " materiaali")
        tallennettavat (keep (partial yksi-tallennettava-materiaalikirjaus muokkaamattomat-materiaali-kirjaukset lisatieto) materiaali-kirjaukset)]
    (when-not muokkaamattomat-materiaali-kirjaukset
      (log "muokkaamattomat kirjaukset puuttuu"))
    (log "tallennettavat: " (pr-str  (vec tallennettavat)))
    tallennettavat))

(defn tallennettava-toimenpide [tehtavat toimenpide urakka tyyppi]
  ;; Toimenpidekoodi tulee eri muodossa luettaessa uutta tai hae:ttaessa valmis
  ;; TODO Yritä yhdistää samaksi muodoksi, ikävää arvailla mistä id löytyy.
  (log "tallennettava-toimenpide: tehtavat " (pr-str tehtavat))
  (let [tehtava (or (::kanavatoimenpide/toimenpidekoodi-id toimenpide)
                    (get-in toimenpide [::kanavatoimenpide/toimenpidekoodi ::toimenpidekoodi/id]))
        materiaalit (::materiaalit/materiaalit toimenpide)]
    (log "tallennus: grid-materiaalit " (pr-str materiaalit))
    (-> toimenpide
        (select-keys [::kanavatoimenpide/id
                      ::kanavatoimenpide/urakka-id
                      ::kanavatoimenpide/suorittaja
                      ::kanavatoimenpide/sopimus-id
                      ::kanavatoimenpide/lisatieto
                      ::kanavatoimenpide/toimenpideinstanssi-id
                      ::kanavatoimenpide/toimenpidekoodi-id
                      ::kanavatoimenpide/pvm
                      ::muokkaustiedot/poistettu?])
        (assoc ::kanavatoimenpide/tyyppi tyyppi
               ::kanavatoimenpide/urakka-id (:id urakka)
               ::kanavatoimenpide/kohde-id (get-in toimenpide [::kanavatoimenpide/kohde ::kohde/id])
               ::kanavatoimenpide/kohteenosa-id (get-in toimenpide [::kanavatoimenpide/kohteenosa ::osa/id])
               ::kanavatoimenpide/huoltokohde-id (get-in toimenpide [::kanavatoimenpide/huoltokohde ::kanavan-huoltokohde/id])
               ::kanavatoimenpide/muu-toimenpide (if (valittu-tehtava-muu? tehtava tehtavat)
                                                     (::kanavatoimenpide/muu-toimenpide toimenpide)
                                                     nil)
               ::kanavatoimenpide/materiaalipoistot (poistettavat-materiaalit toimenpide)
               ::kanavatoimenpide/materiaalikirjaukset (tallennettavat-materiaalit toimenpide))
        (dissoc ::kanavatoimenpide/kuittaaja
                ::kanavatoimenpide/materiaalit))))

(defn tallenna-toimenpide [app {:keys [toimenpide tehtavat valinnat tyyppi
                                       toimenpide-tallennettu toimenpide-ei-tallennettu]}]
  (if (:tallennus-kaynnissa? app)
    app
    (let [toimenpide (tallennettava-toimenpide tehtavat toimenpide (get-in app [:valinnat :urakka]) tyyppi)
          hakuehdot (muodosta-kohteiden-hakuargumentit valinnat tyyppi)]
      (-> app
          (tuck-apurit/post! :tallenna-kanavatoimenpide
                             {::kanavatoimenpide/tallennettava-kanava-toimenpide toimenpide
                              ::kanavatoimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}
                             {:onnistui toimenpide-tallennettu
                              :epaonnistui toimenpide-ei-tallennettu})
          (assoc :tallennus-kaynnissa? true)))))

(defn toimenpide-tallennettu [app toimenpiteet]
  (viesti/nayta! "Toimenpide tallennettu" :success)
  (assoc app :tallennus-kaynnissa? false
             :avattu-toimenpide nil
             :toimenpiteet toimenpiteet))

(defn toimenpide-ei-tallennettu [app]
  (viesti/nayta! "Toimenpiteiden tallentaminen epäonnistui" :danger)
  (assoc app :tallennus-kaynnissa? false))

(defn huoltokohteet-haettu [app huoltokohteet]
  (assoc app :huoltokohteet huoltokohteet
             :huoltokohteiden-haku-kaynnissa? false))

(defn huoltokohteet-ei-haettu [app]
  (viesti/nayta! "Huoltokohteiden haku epäonnistui" :danger)
  (assoc app :huoltokohteiden-haku-kaynnissa? false))

(defn tehtavat-tyypilla [tehtavat tyyppi]
  (filter
    (fn [tehtava]
      (some #(= % tyyppi) (:hinnoittelu tehtava)))
    (map #(nth % 3) tehtavat)))
