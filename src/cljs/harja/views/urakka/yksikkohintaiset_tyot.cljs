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

(def +hoitokauden-alkupvm+ "1.10.yyyy")

(def +hoitokauden-loppupvm+ "30.09.yyyy")

(def valittu-hoitokausi "Hoitokausi" (atom nil))

(defn valitse-hoitokausi! [hk]
  (reset! valittu-hoitokausi hk))

(defn hoitokaudet [alkupvm loppupvm]
  (let [ensimmainen-vuosi (.getYear alkupvm)
        viimeinen-vuosi (.getYear loppupvm)]
    (mapv (fn [vuosi]
              {:alkupvm (pvm/luo-pvm vuosi +hoitokauden-alkukk-indeksi+ +hoitokauden-alkupv-indeksi+)
               :loppupvm (pvm/luo-pvm (inc vuosi) +hoitokauden-loppukk-indeksi+ +hoitokauden-loppupv-indeksi+)})
          (range ensimmainen-vuosi (inc viimeinen-vuosi)))))


(deftk yksikkohintaiset-tyot [ur]
  [tyot (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))
   toimenpiteet-ja-tehtavat (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat (:id ur)))
   kolmostason-tpt nil
   nelostason-tpt nil
   urakan-hoitokaudet nil
   ]
  
  (do
    (run! (let [toimenpiteet-ja-tehtavat (group-by :taso @toimenpiteet-ja-tehtavat)]
            (reset! kolmostason-tpt (vec (get toimenpiteet-ja-tehtavat 3)))
            (reset! nelostason-tpt (vec (get toimenpiteet-ja-tehtavat 4)))
            (reset! urakan-hoitokaudet (hoitokaudet (:alkupvm ur) (:loppupvm ur)))))
    
    ;; varmista järkevät oletusvalinnat
    (if (nil? @valittu-sopimusnumero)
          (valitse-sopimusnumero! (first (:sopimusnumerot ur))))
    (if (nil? @valittu-hoitokausi)
          (valitse-hoitokausi! (first @urakan-hoitokaudet)))

    [:div.yksikkohintaiset-tyot 
     [:div.alasvetovalikot
      [:div.label-ja-alasveto 
       [:span.alasvedon-otsikko "Sopimusnumero"]
       [alasvetovalinta {:valinta @valittu-sopimusnumero
                         :format-fn str
                         :valitse-fn valitse-sopimusnumero!
                         :class "alasveto"
                         }
        (:sopimusnumerot ur)
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
            :tallenna #(tallenna-tyot %)
            :tunniste :id
            :voi-poistaa? false}
           
           ;; sarakkeet
           [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string  :leveys "30%"}
            
            {:otsikko (str "Määrä 10-12/" (.getYear (:alkupvm @valittu-hoitokausi))) :nimi :maara-alku :tyyppi :numero :leveys "15%"}
            {:otsikko (str "Määrä 1-9/" (.getYear (:loppupvm @valittu-hoitokausi))) :nimi :maara-loppu :tyyppi :numero :leveys "15%"}
            {:otsikko "Yks." :nimi :yksikko :tyyppi :string :leveys "5%"}
            {:otsikko (str "\u20AC" "/yks") :nimi :yksikkohinta :tyyppi numero :leveys "10%"}
            {:otsikko "Yhteensä" :nimi :yhteensa :tyyppi :string :leveys "10%"}
            {:otsikko "Toiminnot" :nimi :toiminnot :tyyppi :string :leveys "15%"}
            ]
           @tyot
           ]
          ]))
