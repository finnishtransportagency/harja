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
            [harja.ui.viesti :as viesti])
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

(defn aseta-lomakkeen-tiedot [app toimenpide]
  (let [kohdeosa-vaihtui? (and (some? (get-in app [:avattu-toimenpide ::kanavatoimenpide/kohteenosa]))
                               (not= (::kanavatoimenpide/kohde toimenpide)
                                     (get-in app [:avattu-toimenpide ::kanavatoimenpide/kohde])))
        toimenpide (if kohdeosa-vaihtui?
                     (assoc toimenpide ::kanavatoimenpide/kohteenosa nil)
                     toimenpide)]
    (assoc app :avattu-toimenpide toimenpide)))

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
                      ::muokkaustiedot/poistettu?])
        (assoc ::kanavatoimenpide/tyyppi tyyppi
               ::kanavatoimenpide/urakka-id (:id urakka)
               ::kanavatoimenpide/kohde-id (get-in toimenpide [::kanavatoimenpide/kohde ::kohde/id])
               ::kanavatoimenpide/kohteenosa-id (get-in toimenpide [::kanavatoimenpide/kohteenosa ::osa/id])
               ::kanavatoimenpide/huoltokohde-id (get-in toimenpide [::kanavatoimenpide/huoltokohde ::kanavan-huoltokohde/id])
               ::kanavatoimenpide/muu-toimenpide (if (valittu-tehtava-muu? tehtava tehtavat)
                                                     (::kanavatoimenpide/muu-toimenpide toimenpide)
                                                     nil))
        (dissoc ::kanavatoimenpide/kuittaaja))))

(defn tallenna-toimenpide [app {:keys [toimenpide tehtavat valinnat tyyppi
                                       toimenpide-tallennettu toimenpide-ei-tallennetti]}]
  (if (:tallennus-kaynnissa? app)
    app
    (let [toimenpide (tallennettava-toimenpide tehtavat toimenpide (get-in app [:valinnat :urakka]) tyyppi)
          hakuehdot (muodosta-kohteiden-hakuargumentit valinnat tyyppi)]
      (-> app
          (tuck-apurit/post! :tallenna-kanavatoimenpide
                             {::kanavatoimenpide/tallennettava-kanava-toimenpide toimenpide
                              ::kanavatoimenpide/hae-kanavatoimenpiteet-kysely hakuehdot}
                             {:onnistui toimenpide-tallennettu
                              :epaonnistui toimenpide-ei-tallennetti})
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