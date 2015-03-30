(ns harja.views.urakka.materiaalit
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer-macros [deftk]]
            [harja.tiedot.urakka.materiaalit :as t]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as suunnittelu]
            [harja.ui.grid :as grid]

            [harja.views.kartta.tasot :as tasot]
            [harja.tiedot.navigaatio :as nav]
            
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn pohjavesialueiden-materiaalit
  "Listaa pohjavesialueiden materiaalit ja mahdollistaa kartalta valinnan."
  [opts materiaalit]
  (let [karttavalinta? (atom false)
        valitse-kartalta (fn [e]
                           (.preventDefault e)
                           (if @karttavalinta?
                             (do (tasot/taso-pois! :pohjavesialueet)
                                 (reset! karttavalinta? false)
                                 (swap! nav/tarvitsen-karttaa conj :materiaalit-pohjavesialueet))
                             (do (tasot/taso-paalle! :pohjavesialueet)
                                 (reset! karttavalinta? true)
                                 (swap! nav/tarvitsen-karttaa conj :materiaalit-pohjavesialueet))))]
    (r/create-class
     {:reagent-render
      (fn [opts materiaalit]
        (log "MATSKUJA: " (pr-str materiaalit))
        [:div.pohjavesialueet
         [grid/muokkaus-grid
          {:otsikko "Pohjavesialueiden materiaalit"
           :tyhja "Ei pohjavesialueille kirjattuja materiaaleja."
           :muokkaa-footer (fn [g]
                             [:button.btn.btn-default {:on-click valitse-kartalta}
                              (if @karttavalinta?
                                "Piilota kartta"
                                "Valitse kartalta")])
           
           :muutos (fn [g]
                     (log "pohjavedet muuttui: " g))
           }
          [{:otsikko "Pohjavesialue" :nimi :pohjavesialue :fmt :nimi :leveys "40%"}
           {:otsikko "Materiaali" :nimi :materiaali :fmt :nimi :leveys "25%"}
           {:otsikko "Määrä" :nimi :maara :leveys "25%"}
           {:otsikko "Yks." :nimi :yksikko :hae (comp :yksikko :materiaali) :muokattava? (constantly false) :leveys "5%"}]
          
          materiaalit
         ]])
      
      })))
       
       
  
(deftk materiaalit [ur]
  [;; haetaan kaikki materiaalit urakalle
   urakan-materiaalit (<! (t/hae-urakan-materiaalit (:id ur)))

   ;; valitaan niistä vain valitun sopimuksen ja hoitokauden materiaalit
   materiaalit :reaction (let [[sopimus-id _] @suunnittelu/valittu-sopimusnumero
                               {:keys [alkupvm loppupvm] :as hk} @suunnittelu/valittu-hoitokausi]
                           (log "SOPIMUS: " sopimus-id)
                           (log "HOITOKAUSI: " hk)
                           (filterv #(and (= sopimus-id (:sopimus %))
                                          (pvm/sama-pvm? (:alkupvm %) alkupvm)
                                          (pvm/sama-pvm? (:loppupvm %) loppupvm))
                                    @urakan-materiaalit))

   ;; luokitellaan yleiset materiaalit ja pohjavesialueiden materiaalit
   yleiset-materiaalit :reaction (filterv #(not (contains? % :pohjavesialue)) @materiaalit)
   pohjavesialue-materiaalit :reaction (filterv #(contains? % :pohjavesialue) @materiaalit)
   ]
  
  (let [materiaalikoodit @(t/hae-materiaalikoodit)]
    (log "URAKKA: " (dissoc ur :alue))
    [:div.materiaalit
     [:ul
      (for [m materiaalikoodit]
        [:li (:nimi m) " (" (:yksikko m) ")"])]

     (if (empty? @materiaalit)
       [:div "EI MATSKUJA"]
       [:div  "MATSKUT"])

     (when (= (:tyyppi ur) :hoito)
       [pohjavesialueiden-materiaalit {} pohjavesialue-materiaalit])
     ])) 
