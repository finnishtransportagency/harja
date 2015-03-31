(ns harja.views.urakka.materiaalit
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [kuuntelija] :refer-macros [deftk]]
            [harja.tiedot.urakka.materiaalit :as t]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as suunnittelu]
            [harja.ui.grid :as grid]

            [harja.views.kartta.tasot :as tasot]
            [harja.views.kartta.pohjavesialueet :refer [hallintayksikon-pohjavesialueet-haku]]
            [harja.tiedot.navigaatio :as nav]
            
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn pohjavesialueiden-materiaalit
  "Listaa pohjavesialueiden materiaalit ja mahdollistaa kartalta valinnan."
  [opts materiaalikoodit materiaalit]
  
  (let [karttavalinta? (atom false)
        valitse-kartalta (fn [e]
                           (.preventDefault e)
                           (if @karttavalinta?
                             (do (tasot/taso-pois! :pohjavesialueet)
                                 (reset! karttavalinta? false)
                                 (reset! nav/kartan-koko :S))
                             (do (tasot/taso-paalle! :pohjavesialueet)
                                 (reset! karttavalinta? true)
                                 (reset! nav/kartan-koko :M))))
        g (grid/grid-ohjaus)
        ]
    (kuuntelija
     {}
     (fn [opts materiaalikoodit materiaalit]
        (log "MATSKUJA: " (pr-str materiaalit))
        [:div.pohjavesialueet
         [grid/muokkaus-grid
          {:otsikko "Pohjavesialueiden materiaalit"
           :tyhja "Ei pohjavesialueille kirjattuja materiaaleja."
           :ohjaus g
           :muokkaa-footer (fn [g]
                             [:button.btn.btn-default {:on-click valitse-kartalta}
                              (if @karttavalinta?
                                "Piilota kartta"
                                "Valitse kartalta")])
           
           :muutos (fn [g]
                     (log "pohjavedet muuttui: " g))
           }
          [{:otsikko "Pohjavesialue"
            :tyyppi :haku :lahde hallintayksikon-pohjavesialueet-haku :nayta :nimi
            :nimi :pohjavesialue :fmt :nimi :leveys "40%"}
           {:otsikko "Materiaali"
            :tyyppi :valinta :valinnat materiaalikoodit :valinta-nayta :nimi
            :nimi :materiaali :fmt :nimi :leveys "35%"}
           {:otsikko "Määrä" :nimi :maara :leveys "15%"
            :tyyppi :numero}
           {:otsikko "Yks." :nimi :yksikko :hae (comp :yksikko :materiaali)  :leveys "5%"
            :tyyppi :string :muokattava? (constantly false)}]
          
          materiaalit
          ]])
     :pohjavesialue-klikattu (fn [this pohjavesialue]
                               (grid/lisaa-rivi! g {:pohjavesialue (dissoc pohjavesialue :type :aihe)})
                               (log "hei klikkasit pohjavesialuetta: " (dissoc pohjavesialue :alue))))))
      
     
       
       
  
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
   pohjavesialue-materiaalit :reaction (into {}
                                             (comp (filter #(contains? % :pohjavesialue))
                                                   (map (juxt :id identity)))
                                             @materiaalit)
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
       [pohjavesialueiden-materiaalit {} materiaalikoodit pohjavesialue-materiaalit])
     ])) 
