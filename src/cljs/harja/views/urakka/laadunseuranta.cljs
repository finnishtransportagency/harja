(ns harja.views.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]]
            [bootstrap :as bs]

            [harja.tiedot.navigaatio :as nav]

            [harja.views.urakka.laadunseuranta.tarkastukset :as tarkastukset]))

(defonce valittu-valilehti (atom :tarkastukset))


(defn laadunseuranta []
  [bs/tabs
   {:active valittu-valilehti}
   
   "Poikkeamat/Reklamaatiot"
   :poikkeamat
   [:div "poiketaanpas"]

   "Tarkastukset"
   :tarkastukset
   [tarkastukset/tarkastukset]])

