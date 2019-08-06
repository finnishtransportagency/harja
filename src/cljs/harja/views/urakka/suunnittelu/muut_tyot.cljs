(ns harja.views.urakka.suunnittelu.muut-tyot
  "Urakan 'Muut työt' välilehti, sis. Muutos-, lisä- ja äkilliset hoitotyöt"
  (:require [reagent.core :refer [atom]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki
                                                  alasveto-ei-loydoksia livi-pudotusvalikko vihje]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]

            [harja.loki :refer [log logt tarkkaile!]]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<!]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defn tallenna-tyot [tyot atomi]
  (go (let [ur @nav/valittu-urakka
            sopimusnumero (first @u/valittu-sopimusnumero)
            tyot (map #(assoc % :alkupvm (:alkupvm ur)
                                :loppupvm (:loppupvm ur)
                                :sopimus sopimusnumero) tyot)
            res (<! (muut-tyot/tallenna-muutoshintaiset-tyot (:id @nav/valittu-urakka)
                                                             (into [] tyot)))]
        (reset! atomi res)
        res)))

(defn muut-tyot [ur]
  (let [g (grid/grid-ohjaus)
        jo-valitut-tehtavat (atom nil)]
    (fn [ur]
      (let [toimenpideinstanssit @u/urakan-toimenpideinstanssit
            tehtavat-tasoineen @u/urakan-muutoshintaiset-toimenpiteet-ja-tehtavat
            tehtavat (map #(nth % 3) tehtavat-tasoineen)
            valittu-tpi-id (:tpi_id @u/valittu-toimenpideinstanssi)
            valitut-tyot (doall (filter #(and
                                          (= (:sopimus %) (first @u/valittu-sopimusnumero))
                                          (= (:toimenpideinstanssi %) valittu-tpi-id))
                                        @u/muutoshintaiset-tyot))
            valitun-tpin-tehtavat-tasoineen (urakan-toimenpiteet/toimenpideinstanssin-tehtavat
                                              valittu-tpi-id
                                              toimenpideinstanssit tehtavat-tasoineen)]
        [:div.row.muut-tyot
         [valinnat/urakan-sopimus ur]
         [valinnat/urakan-toimenpide+muut ur]
         [grid/grid
          {:otsikko      "Urakkasopimuksen mukaiset muutos- ja lisätyöhinnat"
           :luokat       ["col-md-10"]
           :tyhja        (if (nil? @u/muutoshintaiset-tyot)
                    [ajax-loader "Muutoshintaisia töitä haetaan..."]
                    "Ei muutoshintaisia töitä")
           :tallenna     (if (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-muutos-ja-lisatyot (:id @nav/valittu-urakka))
                       #(tallenna-tyot
                          % u/muutoshintaiset-tyot)
                       :ei-mahdollinen)
           :tallennus-ei-mahdollinen-tooltip
           (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                           oikeudet/urakat-suunnittelu-muutos-ja-lisatyot)
           :ohjaus       g
           :muutos       #(reset! jo-valitut-tehtavat (into #{} (map (fn [rivi]
                                                                 (:tehtava rivi))
                                                               (vals (grid/hae-muokkaustila %)))))
           :voi-poistaa? (constantly (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-muutos-ja-lisatyot
                                                               (:id @nav/valittu-urakka)))}

          [{:otsikko       "Tehtävä" :nimi :tehtavanimi
            :jos-tyhja     "Ei valittavia tehtäviä"
            :valinta-arvo  #(:nimi (nth % 3))
            :valinta-nayta #(if % (:nimi (nth % 3)) "- Valitse tehtävä -")
            :tyyppi        :valinta
            :validoi       [[:ei-tyhja "Anna tehtävä"]]
            :valinnat-fn   #(sort-by (fn [rivi] (:nimi (nth rivi 3)))
                                     (filter (fn [t]
                                               (not ((disj @jo-valitut-tehtavat (:tehtava %))
                                                      (:id (nth t 3))))) valitun-tpin-tehtavat-tasoineen))
            :muokattava?   #(neg? (:id %))
            :aseta         #(assoc %1 :tehtavanimi %2
                                      :tehtava (:id (urakan-toimenpiteet/tehtava-nimella %2 tehtavat))
                                      :yksikko (:yksikko (urakan-toimenpiteet/tehtava-nimella %2 tehtavat)))
            :leveys        "45%"}
           {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "10%"}
           {:otsikko "Muutoshinta / yksikkö" :nimi :yksikkohinta :tasaa :oikea
            :validoi [[:ei-tyhja "Anna muutoshinta"]]
            :tyyppi :numero :fmt fmt/euro-opt :leveys "20%"}]

          valitut-tyot]

         [vihje yleiset/+tehtavien-hinta-vaihtoehtoinen+ "col-xs-12"]]))))
