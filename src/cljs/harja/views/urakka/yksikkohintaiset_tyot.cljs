(ns harja.views.urakka.yksikkohintaiset-tyot
  "Urakan 'Yksikkohintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? alasveto-ei-loydoksia alasvetovalinta radiovalinta]]
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


(def valittu-sopimusnumero "Sopimusnumero" (atom nil))

(defn valitse-sopimusnumero! [sn]
  (reset! valittu-sopimusnumero sn)
  )

(def +hoitokauden-alkukk-indeksi+ "9")
(def +hoitokauden-alkupv-indeksi+ "1")
(def +hoitokauden-loppukk-indeksi+ "8")
(def +hoitokauden-loppupv-indeksi+ "30")

(def valittu-hoitokausi "Hoitokausi" (atom nil))

(defn valitse-hoitokausi! [hk]
  (reset! valittu-hoitokausi hk))

(defn hoitokaudet [ur]
  (let [ensimmainen-vuosi (.getYear (:alkupvm ur))
        viimeinen-vuosi (.getYear (:loppupvm ur))]
    (mapv (fn [vuosi]
              {:alkupvm (pvm/luo-pvm vuosi +hoitokauden-alkukk-indeksi+ +hoitokauden-alkupv-indeksi+)
               :loppupvm (pvm/luo-pvm (inc vuosi) +hoitokauden-loppukk-indeksi+ +hoitokauden-loppupv-indeksi+)})
          (range ensimmainen-vuosi (inc viimeinen-vuosi)))))

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
   urakan-hoitokaudet nil
   tyorivit nil 
   ]
  
  (do
    (run! (let [toimenpiteet-ja-tehtavat (group-by :taso @toimenpiteet-ja-tehtavat)]
            (reset! kolmostason-tpt (vec (get toimenpiteet-ja-tehtavat 3)))
            (reset! nelostason-tpt (vec (get toimenpiteet-ja-tehtavat 4)))
            (reset! urakan-hoitokaudet (hoitokaudet ur))))
    
    ;; vain valitun hoitokauden tyot talteen
    (run! (let [tehtavien-rivit (group-by :tehtava (filter (fn [t]
                                                             (and 
                                                               (= (:sopimus t) (first @valittu-sopimusnumero))
                                                               (or
                                                                 (pvm/sama-pvm? (:alkupvm t) (:alkupvm @valittu-hoitokausi))
                                                                 (pvm/sama-pvm? (:loppupvm t) (:loppupvm @valittu-hoitokausi))))
                                                             ) @tyot))
                kirjatut-tehtavat (into #{} (keys tehtavien-rivit))
                tyhjat-tyot  (map #(luo-tyhja-tyo % ur @valittu-hoitokausi) (filter (fn [tp]
                                                                  (not (kirjatut-tehtavat (:id tp)))) @nelostason-tpt))]
            
            (reset! tyorivit
                           (concat (mapv (fn [[_ tehtavan-rivit]]
                                                       (let [pohja (first tehtavan-rivit)]
                                                         (merge pohja
                                                                (zipmap (map #(if (= (.getYear (:alkupvm @valittu-hoitokausi))
                                                                                     (.getYear (:alkupvm %)))
                                                                                :maara-kkt-10-12 :maara-kkt-1-9) tehtavan-rivit)
                                                                        (map :maara tehtavan-rivit))
                                                                
                                                                {:yhteensa (reduce + 0 (map #(* (:yksikkohinta %) (:maara %)) tehtavan-rivit))}
                                                                ))) tehtavien-rivit) tyhjat-tyot))))
    ;; varmista järkevät oletusvalinnat
    (if (nil? @valittu-sopimusnumero)
      (valitse-sopimusnumero! (first (:sopimukset ur))))
    (if (nil? @valittu-hoitokausi)
      (valitse-hoitokausi! (first @urakan-hoitokaudet)))
    
    [:div.yksikkohintaiset-tyot 
     [:div.alasvetovalikot
      [:div.label-ja-alasveto 
       [:span.alasvedon-otsikko "Sopimusnumero"]
       [alasvetovalinta {:valinta @valittu-sopimusnumero
                         :format-fn second
                         :valitse-fn valitse-sopimusnumero!
                         :class "alasveto"
                         }
        (:sopimukset ur)
        ]]
      [:div.label-ja-alasveto
       [:span.alasvedon-otsikko "Hoitokausi"]
       [alasvetovalinta {:valinta @valittu-hoitokausi
                         ;;\u2014 on väliviivan unikoodi
                         :format-fn #(if % (str (pvm/pvm (:alkupvm %)) 
                                                " \u2014 " (pvm/pvm (:loppupvm %))) "Valitse")
                         :valitse-fn valitse-hoitokausi!
                         :class "alasveto"
                         }
        @urakan-hoitokaudet
        ]]]
     
     [grid/grid
      {:otsikko "Yksikköhintaiset työt"
       :tyhja (if (nil? nelostason-tpt) [ajax-loader "Yksikköhintaisia töitä haetaan..."] "Ei yksikköhintaisia töitä")
       :tallenna #(tallenna-tyot ur @valittu-sopimusnumero @valittu-hoitokausi 
                                 tyot @tyorivit %)
       :tunniste :tehtava
       :voi-lisata? false
       :voi-poistaa? (constantly false)
       }
      
      ;; sarakkeet
      [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string  :leveys "25%"}
       
       {:otsikko (str "Määrä 10-12/" (.getYear (:alkupvm @valittu-hoitokausi))) :nimi :maara-kkt-10-12 :tyyppi :numero :leveys "15%"}
       {:otsikko (str "Määrä 1-9/"  (.getYear (:loppupvm @valittu-hoitokausi))) :nimi :maara-kkt-1-9 :tyyppi :numero :leveys "15%"}
       {:otsikko "Yks." :nimi :yksikko :tyyppi :string :leveys "15%"}
       {:otsikko (str "\u20AC" "/yks") :nimi :yksikkohinta :tyyppi :numero :leveys "15%"}
       {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :string :leveys "15%" :fmt #(if % (str (.toFixed % 2) " \u20AC"))}
       ]
      @tyorivit
      ]
     ]))


  