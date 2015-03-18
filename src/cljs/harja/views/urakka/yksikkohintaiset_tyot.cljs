(ns harja.views.urakka.yksikkohintaiset-tyot
  "Urakan 'Yksikkohintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? alasveto-ei-loydoksia alasvetovalinta radiovalinta]]
            [harja.tiedot.urakka.suunnittelu :as suunnittelu]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            
            
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.ui.yleiset :refer [deftk]]))


(defn tallenna-tyot [ur sopimusnumero hoitokausi tyot vanhat-tyot uudet-tyot]
  (log "tallenna-tyot enter, uudet työt" uudet-tyot)
  (go (let [muuttuneet
            (into []
                  ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                  uudet-tyot)
            
            res (<! (yks-hint-tyot/tallenna-urakan-yksikkohintaiset-tyot (:id ur) sopimusnumero hoitokausi muuttuneet))]
        (reset! tyot (map #(pvm/muunna-aika % :alkupvm :loppupvm) res))
        true)));;)

(defn luo-tyhja-tyo [tp ur hk]
  {:tehtava (:id tp), :tehtavan_nimi (:nimi tp) :urakka (:id ur) 
   :alkupvm (:alkupvm hk) :loppupvm (:loppupvm hk)})


(deftk yksikkohintaiset-tyot [ur]
  [tyot (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))
   toimenpiteet-ja-tehtavat (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat (:id ur)))
   kolmostason-tpt nil
   nelostason-tpt nil
   tyorivit nil 
   ]
  
  (do
    (run! (let [toimenpiteet-ja-tehtavat (group-by :taso @toimenpiteet-ja-tehtavat)]
            (reset! kolmostason-tpt (vec (get toimenpiteet-ja-tehtavat 3)))
            (reset! nelostason-tpt (vec (get toimenpiteet-ja-tehtavat 4)))))
    
    ;; vain valitun hoitokauden tyot talteen
    (run! (let [tehtavien-rivit (group-by :tehtava (filter (fn [t]
                                                             (and 
                                                               (= (:sopimus t) (first @suunnittelu/valittu-sopimusnumero))
                                                               (or
                                                                 (pvm/sama-pvm? (:alkupvm t) (:alkupvm @suunnittelu/valittu-hoitokausi))
                                                                 (pvm/sama-pvm? (:loppupvm t) (:loppupvm @suunnittelu/valittu-hoitokausi))))
                                                             ) @tyot))
                kirjatut-tehtavat (into #{} (keys tehtavien-rivit))
                tyhjat-tyot  (map #(luo-tyhja-tyo % ur @suunnittelu/valittu-hoitokausi) (filter (fn [tp]
                                                                                      (not (kirjatut-tehtavat (:id tp)))) @nelostason-tpt))]
            
            (reset! tyorivit
                    (vec (concat (mapv (fn [[_ tehtavan-rivit]]
                                         (let [pohja (first tehtavan-rivit)]
                                           (merge pohja
                                                  (zipmap (map #(if (= (.getYear (:alkupvm @suunnittelu/valittu-hoitokausi))
                                                                       (.getYear (:alkupvm %)))
                                                                  :maara-kkt-10-12 :maara-kkt-1-9) tehtavan-rivit)
                                                          (map :maara tehtavan-rivit))
                                                  
                                                  {:yhteensa (reduce + 0 (map #(* (:yksikkohinta %) (:maara %)) tehtavan-rivit))}
                                                  ))) tehtavien-rivit) tyhjat-tyot)))))
    
    [:div.yksikkohintaiset-tyot     
     [grid/grid
      {:otsikko "Yksikköhintaiset työt"
       :tyhja (if (nil? nelostason-tpt) [ajax-loader "Yksikköhintaisia töitä haetaan..."] "Ei yksikköhintaisia töitä")
       :tallenna #(tallenna-tyot ur @suunnittelu/valittu-sopimusnumero @suunnittelu/valittu-hoitokausi 
                                 tyot @tyorivit %)
       :tunniste :tehtava
       :voi-lisata? false
       :voi-poistaa? (constantly false)
       }
      
      ;; sarakkeet
      [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string :muokattava? (constantly false) :leveys "25%"}
       {:otsikko (str "Määrä 10-12/" (.getYear (:alkupvm @suunnittelu/valittu-hoitokausi))) :nimi :maara-kkt-10-12 :tyyppi :numero :leveys "15%"}
       {:otsikko (str "Määrä 1-9/"  (.getYear (:loppupvm @suunnittelu/valittu-hoitokausi))) :nimi :maara-kkt-1-9 :tyyppi :numero :leveys "15%"}
       {:otsikko "Yks." :nimi :yksikko :tyyppi :string :leveys "15%"}
       {:otsikko (str "\u20AC" "/yks") :nimi :yksikkohinta :tyyppi :numero :leveys "15%"}
       {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :string :muokattava? (constantly false) :leveys "15%" :fmt #(if % (str (.toFixed % 2) " \u20AC"))}
       ]
      @tyorivit
      ]
     ]))


