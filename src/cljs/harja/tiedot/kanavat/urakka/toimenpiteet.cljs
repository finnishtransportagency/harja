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
            [harja.asiakas.kommunikaatio :as k]
            [clojure.string :as str]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.pvm :as pvm]
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
                                 ::kayttaja/sukunimi (:sukunimi kayttaja)}
   ::kanavatoimenpide/pvm (pvm/nyt)})

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


(defn toimenpiteen-materiaalimuutos? [tp muutos]
  (when (some? (::materiaalit/toimenpide muutos))
    (= (::materiaalit/toimenpide muutos) (::kanavatoimenpide/id tp))))

(defn materiaalilistaus->grid [toimenpide listaukset]
  (mapcat (fn [materiaalilistaus]
            (transduce
             (comp
               (filter (partial toimenpiteen-materiaalimuutos? toimenpide))
              ;; Materiaalit gridissä on :maara, :yksikko ja :tallennetut-materiaalit nimiset sarakkeet. Materiaalin
              ;; nimi, yksikko, urakka-id, pvm ja id tarvitaan tallentamista varten.
              (map #(identity {:maara (- (::materiaalit/maara %))
                               :yksikko (::materiaalit/yksikko materiaalilistaus)
                               :tallennetut-materiaalit {::materiaalit/nimi (::materiaalit/nimi materiaalilistaus)
                                         ::materiaalit/urakka-id (::materiaalit/urakka-id materiaalilistaus)
                                         ::materiaalit/pvm (::materiaalit/pvm %)
                                         ::materiaalit/id (::materiaalit/id %)
                                         ::materiaalit/yksikko (::materiaalit/yksikko materiaalilistaus)}})))
             conj (::materiaalit/muutokset materiaalilistaus)))
          listaukset))

(defn aseta-lomakkeen-tiedot [app toimenpide]
  (let [vanhat-materiaalit (-> app :avattu-toimenpide ::materiaalit/materiaalit)

        vanha-id (-> app :avattu-toimenpide ::kanavatoimenpide/id)
        uusi-id (::kanavatoimenpide/id toimenpide)

        kohdeosa-vaihtui? (and (some? (get-in app [:avattu-toimenpide ::kanavatoimenpide/kohteenosa]))
                               (not= (::kanavatoimenpide/kohde toimenpide)
                                     (get-in app [:avattu-toimenpide ::kanavatoimenpide/kohde])))
        toimenpide (if kohdeosa-vaihtui?
                     (assoc toimenpide ::kanavatoimenpide/kohteenosa nil)
                     toimenpide)
        materiaalilistaukset (:urakan-materiaalit app)
        materiaalit (if (= vanha-id uusi-id)
                      vanhat-materiaalit
                      (materiaalilistaus->grid toimenpide materiaalilistaukset))
        toimenpide (assoc toimenpide ::materiaalit/materiaalit materiaalit
                          ::materiaalit/muokkaamattomat-materiaalit (filter #(not (:jarjestysnumero %)) materiaalit))]
    (assoc app :avattu-toimenpide toimenpide)))


;; materiaalien tietomallissa backilla:
;; vv_materiaalilistaus on view, sisältää rivin per materiaali (esim Naulat) ja maara-nyt arvon +muutokset-arrayn.
;; sisältö tehdään vv_materiaali-taulusta.
;; vv_materiaali aka materiaalikirjaus on yhtä muutosta esittävä rivi.
;; front-koodissa taas materiaalikirjauksiksi kutsutaan myös grid-mappeina olevia kirjauksiksi.
;;
;; ::materiaalit/materiaalit -avaimen alla oleva map sisaltaa :tallennetut-materiaalit avaimen alla listaus-viewin muotoa,
;; {:jarjestysnumero 0,
;;  :tallennetut-materiaalit {::materiaalit/urakka-id 31,
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
  (for [muutos (::materiaalit/muutokset grid-rivi)]
    {:maara (- (::materiaalit/maara muutos))
     :tallennetut-materiaalit {::materiaalit/nimi (::materiaalit/nimi grid-rivi)
               ::materiaalit/urakka-id (::materiaalit/urakka-id grid-rivi)
               ::materiaalit/pvm (::materiaalit/pvm muutos)
               ::materiaalit/id (::materiaalit/id muutos)}}))

(defn materiaalikirjaus->poistettavat-1 [{:keys [poistettu jarjestysnumero tallennetut-materiaalit]}]
  ;; poistetaan mapista poistetuksi merkatut uudet rivit
  (when (and poistettu (not jarjestysnumero))
    (select-keys tallennetut-materiaalit #{::materiaalit/id ::materiaalit/urakka-id})))


(defn materiaalikirjaus->poistettavat-2 [nykygrid {:keys [jarjestysnumero tallennetut-materiaalit]}]
  ;; poistetaan materiaalit jotka löytyy vain muokkaamattomista materiaaleista

  (let [nykygridin-samannimiset (filter
                                 #(= (::materiaalit/nimi tallennetut-materiaalit) (-> % :tallennetut-materiaalit ::materiaalit/nimi))
                                 nykygrid)]
    ;; (log "samannimiset:" (pr-str nykygridin-samannimiset))
    ;; pyydetään backilta poistettavaksi, jos 1) ei loydy saman nimisiä nykygridistä
    ;;                                     ja 2) jarjestysnumero puuttuu eli on backilta ladattu (vs frontilla tallentamatta lisätty)
    (when (empty? nykygridin-samannimiset)
      ;; (log "not jnr:" (pr-str (not jarjestysnumero)))
      (when (not jarjestysnumero)

        (select-keys tallennetut-materiaalit #{::materiaalit/id ::materiaalit/urakka-id})))))

(defn poistettavat-materiaalit [tp]
  (concat
   (keep materiaalikirjaus->poistettavat-1 (::materiaalit/materiaalit tp))
   (keep (partial materiaalikirjaus->poistettavat-2 (::materiaalit/materiaalit tp))
         (::materiaalit/muokkaamattomat-materiaalit tp))))

(defn yksi-tallennettava-materiaalikirjaus
  "Palauttaa tallennettavan mapin kun m-kirjaus on muokattu, ei-tyhja, ei-poistettu grid-rivi tyyliin {:tallennetut-materiaalit ... :maara ...} - muutoin nil"
  [muokkaamattomat-kirjaukset lisatieto paivamaara m-kirjaus]

  (let [muokkaamaton? (if (seq muokkaamattomat-kirjaukset)
                       (first (filter (partial = m-kirjaus) muokkaamattomat-kirjaukset))
                       false)
        tyhja? (= [:jarjestysnumero] (keys m-kirjaus))
        poistettu? (:poistettu m-kirjaus)
        materiaali (dissoc (:tallennetut-materiaalit m-kirjaus)
                        ::materiaalit/maara-nyt ::materiaalit/halytysraja
                        ::materiaalit/muutokset ::materiaalit/alkuperainen-maara)]
    ;; jatetaan tallentamatta tyhjat, poistetut, muokkaamattomat.
    (if (or tyhja? poistettu? muokkaamaton?)
      nil
      (assoc materiaali
             ::materiaalit/pvm paivamaara
             ::materiaalit/maara (- (:maara m-kirjaus)) ;; muutetaan miinusmerkkiseksi (muuten tulee merkattua lisäystä eikä käyttöä)
             ::materiaalit/lisatieto (or lisatieto "Käytetty toimenpiteen kirjauksesssa")))))

(defn tallennettavat-materiaalit [tp]
  (if (::muokkaustiedot/poistettu? tp)
    []
    ;; else
    (let [materiaali-kirjaukset (::materiaalit/materiaalit tp)
          muokkaamattomat-materiaali-kirjaukset (filter
                                                  ;; Lasketaan muokkaamattoksi vain ne, joilla on
                                                  ;; myös sama pvm kuin tallennettavalla toimenpiteellä
                                                  #(= (::materiaalit/pvm %) (::kanavatoimenpide/pvm tp))
                                                  (::materiaalit/muokkaamattomat-materiaalit tp))

          kohteen-nimi (-> tp ::kanavatoimenpide/kohde ::kohde/nimi)
          kohteen-lisatieto (::kanavatoimenpide/lisatieto tp)

          lisatieto (str "Käytetty kohteessa: " kohteen-nimi
                         (when kohteen-lisatieto (str ", Lisätietona: " kohteen-lisatieto)))
          tallennettavat (keep (partial yksi-tallennettava-materiaalikirjaus muokkaamattomat-materiaali-kirjaukset lisatieto (::kanavatoimenpide/pvm tp)) materiaali-kirjaukset)]
      tallennettavat)))

(defn tallennettava-toimenpide [tehtavat toimenpide urakka tyyppi]
  ;; Toimenpidekoodi tulee eri muodossa luettaessa uutta tai hae:ttaessa valmis
  ;; TODO Yritä yhdistää samaksi muodoksi, ikävää arvailla mistä id löytyy.

  (let [tehtava (or (::kanavatoimenpide/toimenpidekoodi-id toimenpide)
                    (get-in toimenpide [::kanavatoimenpide/toimenpidekoodi ::toimenpidekoodi/id]))]
    (-> toimenpide
        (select-keys [::kanavatoimenpide/id
                      ::kanavatoimenpide/urakka-id
                      ::kanavatoimenpide/suorittaja
                      ::kanavatoimenpide/sopimus-id
                      ::kanavatoimenpide/lisatieto
                      ::kanavatoimenpide/toimenpideinstanssi-id
                      ::kanavatoimenpide/toimenpidekoodi-id
                      ::kanavatoimenpide/pvm
                      ::kanavatoimenpide/sijainti
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

(defn tallenna-toimenpide [app {:keys [toimenpide tehtavat valinnat tyyppi poisto?
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
                              :onnistui-parametrit [poisto?]
                              :epaonnistui toimenpide-ei-tallennettu
                              :epaonnistui-parametrit [poisto?]})
          (assoc :tallennus-kaynnissa? true)))))

(defn poista-valittu-toimenpide [valitut-toimenpide-idt poistettava-id]
  (set (remove #(= % poistettava-id) valitut-toimenpide-idt)))

(defn toimenpide-tallennettu [app uudet-toimenpiteet uusi-materiaalilistaus poisto?]
  (if poisto?
    (viesti/nayta! "Toimenpide poistettu" :success)
    (viesti/nayta! "Toimenpide tallennettu" :success))
  (assoc app :tallennus-kaynnissa? false
         :avattu-toimenpide nil
         :urakan-materiaalit uusi-materiaalilistaus
         :toimenpiteet uudet-toimenpiteet))

(defn toimenpide-ei-tallennettu [app poisto?]
  (if poisto?
    (viesti/nayta! "Toimenpiteiden poisto epäonnistui" :danger)
    (viesti/nayta! "Toimenpiteiden tallentaminen epäonnistui" :danger))
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

(defn toimenpiteiden-toiminto-suoritettu [toimenpiteiden-lkm toiminto]
  (str toimenpiteiden-lkm " "
       (if (= 1 toimenpiteiden-lkm) "toimenpide" "toimenpidettä")
       " " toiminto "."))


