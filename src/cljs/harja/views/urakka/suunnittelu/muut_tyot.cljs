(ns harja.views.urakka.suunnittelu.muut-tyot
  "Urakan 'Muut työt' välilehti, sis. Muutos-, lisä- ja äkilliset hoitotyöt"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      alasveto-ei-loydoksia livi-pudotusvalikko radiovalinta]
             :as yleiset]
            [harja.ui.visualisointi :as vis]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.muut-tyot :as muut-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.tiedot.istunto :as istunto]

            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]

            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))



(defn tallenna-tyot [tyot atomi]
  (log "tallenna-muut-tyot" (pr-str tyot)))

(defn ryhmittele-tehtavat
      "Ryhmittelee 4. tason tehtävät. Lisää väliotsikot eri tehtävien väliin"
  [toimenpiteet-tasoittain tyorivit]
  (let [otsikko (fn [{:keys [tehtava]}]
                  (or
                    (some (fn [[t1 t2 t3 t4]]
                            (when (= (:id t4) tehtava)
                              (str (:nimi t2) " / " (:nimi t3))))
                      toimenpiteet-tasoittain)
                    "Muut tehtävät"))
        otsikon-mukaan (group-by otsikko tyorivit)]
    (mapcat (fn [[otsikko rivit]]
              (concat [(grid/otsikko otsikko)] rivit))
      (seq otsikon-mukaan))))

(defn sarakkeet []
  [{:otsikko "Toimenpide" :nimi :toimenpideinstanssi :tyyppi :string :muokattava? (constantly false) :leveys "30%"}
   {:otsikko "Tehtävä" :nimi :tehtavan_nimi
    :tyyppi :valinta
    :valinta-nayta :tpi_nimi
    :valinnat      [1 2 3]
    :leveys "30%"}
   {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "7%"}
   {:otsikko (str "Yksikköhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero :fmt fmt/euro-opt :leveys "10%"}])



(defn muut-tyot []
  (let [muutoshintaiset-tyot
        (reaction<! (let [ur (:id @nav/valittu-urakka)
                          sivu @u/suunnittelun-valittu-valilehti]
                      (when (and ur (= :muut sivu))
                        (muut-tyot/hae-urakan-muutoshintaiset-tyot ur))))
        tehtavat-tasoineen @u/urakan-toimenpiteet-ja-tehtavat
        tehtavat (map #(nth % 3) tehtavat-tasoineen)
        toimenpideinstanssit @u/urakan-toimenpideinstanssit
        hae-tpin-nimi (fn [tpi-id]
                        (log "hae tpin nimi" tpi-id)
                        (:tpi_nimi (first (filter #(= (:tpi_id %) tpi-id)
                                            toimenpideinstanssit))))
        tpin-tehtavat (fn [tpi-id]
                       (let [tpin-tpkoodi (:id (first (filter #(= (:tpi_id %) tpi-id) toimenpideinstanssit)))]
                         (into []
                           (filter (fn [t]
                                     (= (:id (nth t 2)) tpin-tpkoodi)) tehtavat-tasoineen))))
        ;{:loppupvm #<20100930T000000>, :yksikko "tiekm", :tehtava 2756, :urakka 1, :yksikkohinta 4.5,
        ; :toimenpideinstanssi 1, :id 8, :sopimus 1, :alkupvm #<20051001T000000>, :tehtavanimi "I rampit"
        luo-tyhja-tyo (fn []
          {:tehtavanimi nil, :toimenpideinstanssi nil :yksikko nil :yksikkohinta nil
           :urakka (:id @nav/valittu-urakka)
           :alkupvm (:alkupvm @nav/valittu-urakka) :loppupvm (:loppupvm @nav/valittu-urakka)})

        _ (log "tehtävät" (pr-str tehtavat))
        _ (log "tehtavat-tasoineen" (pr-str tehtavat-tasoineen))
        _ (log "toimenpideinstanssit" (pr-str toimenpideinstanssit))
        ]
    (tarkkaile! "muutoshintaiset-tyot"  muutoshintaiset-tyot)
    (komp/luo
      (fn []
        [:div.muut-tyot
         [:div "Keskeneräinen ominaisuus - ethän kirjaa bugeja vielä, kiitos."]

         [grid/grid
          {:otsikko  "Muutos- ja lisätyöhinnat"
           :tyhja    (if (nil? @muutoshintaiset-tyot) [ajax-loader "Muutoshintaisia töitä haetaan..."] "Ei muutoshintaisia töitä")
           :tallenna (istunto/jos-rooli-urakassa istunto/rooli-urakanvalvoja
                       (:id @nav/valittu-urakka)
                       #(tallenna-tyot
                         % muutoshintaiset-tyot)
                       :ei-mahdollinen)
           :tunniste :tehtavanimi}

          [{:otsikko       "Toimenpide" :nimi :toimenpideinstanssi
            :tyyppi        :valinta
            :fmt           #(hae-tpin-nimi %)
            :valinta-arvo  :tpi_id
            :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
            :valinnat toimenpideinstanssit :leveys "25%"
            :aseta #(assoc %1 :toimenpideinstanssi %2
                              :tehtavanimi nil)
            :muokattava? #(neg? (:id %))}
           {:otsikko       "Tehtävä" :nimi :tehtavanimi
            :valinta-arvo  #(:nimi (nth % 3))
            :valinta-nayta #(if % (:nimi (nth % 3)) "- Valitse tehtävä -")
            :tyyppi        :valinta
            :valinnat-fn   #(tpin-tehtavat (:toimenpideinstanssi %))
            :muokattava?   #(neg? (:id %))
            :leveys        "45%"}
           {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "10%"}
           {:otsikko (str "Yksikköhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero :fmt fmt/euro-opt :leveys "20%"}]

          @muutoshintaiset-tyot]]))))


