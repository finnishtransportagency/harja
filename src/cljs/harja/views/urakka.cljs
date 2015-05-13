(ns harja.views.urakka
  "Urakan näkymät: sisältää urakan perustiedot ja tabirakenteen"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.asiakas.tapahtumat :as t]

            [harja.views.urakka.yleiset :as urakka-yleiset]
            [harja.views.urakka.suunnittelu :as suunnittelu]
            [harja.views.urakka.toteumat :as toteumat]
            [harja.views.urakka.siltatarkastukset :as siltatarkastukset]
            [harja.views.urakka.maksuerat :as maksuerat]
            [harja.tiedot.urakka.yhteystiedot :as yht]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]))

(def valittu-valilehti "Valittu välilehti" (atom :yleiset))

(defn urakka
  "Urakkanäkymä"
  [ur]
  
  [bs/tabs {:style :tabs :active valittu-valilehti}

   "Yleiset"
   :yleiset
   ^{:key "yleiset"}
   [urakka-yleiset/yleiset ur]

   "Suunnittelu"
   :suunnittelu
   ^{:key "suunnittelu"}
   [suunnittelu/suunnittelu ur]

   "Toteumat"
   :toteumat
   ^{:key "toteumat"}
   [toteumat/toteumat ur]

   "Laadunseuranta"
   :laadunseuranta
   ^{:key "laadunseuranta"}
   [:div
    "laatua vois toki seurata"]

   "Siltatarkastukset"
   :siltatarkastukset
   (when (= :hoito (:tyyppi ur))
     ^{:key "siltatarkastukset"}
     [siltatarkastukset/siltatarkastukset ur])

   "Välitavoitteet"
   :valitavoitteet
   (when-not (= :hoito (:tyyppi ur))
       ^{:key "valitavoitteet"}
       [valitavoitteet/valitavoitteet ur])

   "Maksuerät"
   :maksuerat
   ^{:key "maksuerat"}
    [maksuerat/maksuerat ur]
   ])
  
 
