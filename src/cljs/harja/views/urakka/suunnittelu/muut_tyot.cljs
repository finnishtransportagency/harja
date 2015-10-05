(ns harja.views.urakka.suunnittelu.muut-tyot
  "Urakan 'Muut työt' välilehti, sis. Muutos-, lisä- ja äkilliset hoitotyöt"
  (:require [reagent.core :refer [atom]]
            [harja.domain.roolit :as roolit]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      alasveto-ei-loydoksia livi-pudotusvalikko radiovalinta]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.muut-tyot :as muut-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]

            [harja.loki :refer [log logt tarkkaile!]]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<!]]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.valinnat :as valinnat])

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

(defn ryhmittele-tehtavat
      "Ryhmittelee 4. tason tehtävät. Lisää väliotsikot eri tehtävien väliin"
  [toimenpiteet-tasoittain tyorivit]
  (let [otsikko (fn [{:keys [tehtava]}]
                  (or
                    (some (fn [[_ t2 t3 t4]]
                            (when (= (:id t4) tehtava)
                              (str (:nimi t2) " / " (:nimi t3))))
                      toimenpiteet-tasoittain)
                    "Muut tehtävät"))
        otsikon-mukaan (group-by otsikko tyorivit)]
    (mapcat (fn [[otsikko rivit]]
              (concat [(grid/otsikko otsikko)] rivit))
      (seq otsikon-mukaan))))

(defn muut-tyot [ur]
  (let [tehtavat-tasoineen @u/urakan-toimenpiteet-ja-tehtavat
        tehtavat (map #(nth % 3) tehtavat-tasoineen)
        toimenpideinstanssit @u/urakan-toimenpideinstanssit
        ryhmitellyt-muutoshintaiset-tyot (reaction (ryhmittele-tehtavat tehtavat-tasoineen @u/muutoshintaiset-tyot))
        g (grid/grid-ohjaus)
        jo-valitut-tehtavat (atom nil)]
    (komp/luo
      (fn []
        [:div.muut-tyot
         [valinnat/urakan-sopimus ur]
         [kartta/kartan-paikka]
         [grid/grid
          {:otsikko      "Muutos- ja lisätyöhinnat"
           :tyhja        (if (nil? @u/muutoshintaiset-tyot)
                           [ajax-loader "Muutoshintaisia töitä haetaan..."]
                           "Ei muutoshintaisia töitä")
           :tallenna     (roolit/jos-rooli-urakassa roolit/urakanvalvoja
                                                    (:id @nav/valittu-urakka)
                                                    #(tallenna-tyot
                                                      % u/muutoshintaiset-tyot)
                                                    :ei-mahdollinen)
           :ohjaus       g
           :muutos       #(reset! jo-valitut-tehtavat (into #{} (map (fn [rivi]
                                                                        (:tehtava rivi))
                                                                      (vals (grid/hae-muokkaustila %)))))
           :voi-poistaa? #(roolit/roolissa? roolit/jarjestelmavastuuhenkilo)}

          [{:otsikko       "Toimenpide" :nimi :toimenpideinstanssi
            :tyyppi        :valinta
            :fmt           #(:tpi_nimi (urakan-toimenpiteet/toimenpideinstanssi-idlla % toimenpideinstanssit))
            :valinta-arvo  :tpi_id
            :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
            :valinnat      toimenpideinstanssit
            :leveys "25%"
            :aseta         #(assoc %1 :toimenpideinstanssi %2
                                      :tehtavanimi nil)
            :muokattava?   #(neg? (:id %))}
           {:otsikko       "Tehtävä" :nimi :tehtavanimi
            :valinta-arvo  #(:nimi (nth % 3))
            :valinta-nayta #(if % (:nimi (nth % 3)) "- Valitse tehtävä -")
            :tyyppi        :valinta
            :valinnat-fn   #(urakan-toimenpiteet/toimenpideinstanssin-tehtavat
                               (:toimenpideinstanssi %)
                               toimenpideinstanssit (filter (fn [t]
                                                              (not ((disj @jo-valitut-tehtavat (:tehtava %))
                                                                     (:id (nth t 3))))) tehtavat-tasoineen))
            :muokattava?   #(neg? (:id %))
            :aseta         #(assoc %1 :tehtavanimi %2
                                      :tehtava (:id (urakan-toimenpiteet/tehtava-nimella %2 tehtavat))
                                      :yksikko (:yksikko (urakan-toimenpiteet/tehtava-nimella %2 tehtavat)))
            :leveys        "45%"}
           {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "10%"}
           {:otsikko "Muutoshinta" :nimi :yksikkohinta :tasaa :oikea
            :validoi [[:ei-tyhja "Anna muutoshinta / yksikkö"]]
            :tyyppi :positiivinen-numero :fmt fmt/euro-opt :leveys "20%"}]

          @ryhmitellyt-muutoshintaiset-tyot]]))))

