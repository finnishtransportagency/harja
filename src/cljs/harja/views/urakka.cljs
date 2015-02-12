(ns harja.views.urakka
  "Urakan näkymät: sisältää urakan perustiedot ja tabirakenteen"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.asiakas.tapahtumat :as t]

            [harja.views.urakka.yleiset :as urakka-yleiset]
            [harja.tiedot.urakka.yhteystiedot :as yht]))

(defn urakka
  "Urakkanäkymä"
  [ur]
  (.log js/console "URAKKA ON : " (pr-str ur))
  
  [bs/tabs {}
   "Yleiset"
   ^{:key "yleiset"}
   [urakka-yleiset/yleiset ur]
   
   
   "Suunnittelu"
   ^{:key "suunnittelu"}
   [:div 
    [bs/dropdown-panel {} "Kustannussuunnitelma: kokonaishintaiset työt" "ei vielä"]
    [bs/dropdown-panel {} "Kustannussuunnitelma: yksikköhintaiset työt" "ei vielä"]
    [bs/dropdown-panel {} "Materiaalisuunnitelma" "ei vielä"]]
   
   "Toteumat"
   ^{:key "toteumat"}
   [:div
    [bs/dropdown-panel {} "Toteutuneet työt" "ei vielä"]
    [bs/dropdown-panel {} "Toteutuneet materiaalit" "ei vielä"]]

   "Laadunseuranta"
   ^{:key "laadunseuranta"}
   [:div
    "laatua vois toki seurata"]

   "Siltatarkastukset"
   ^{:key "siltatarkastukset"}
   [:div
    "siltojakin voisi tarkastella"]
   
   ])
  
 
