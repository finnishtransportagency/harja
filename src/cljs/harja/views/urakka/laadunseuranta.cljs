(ns harja.views.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]]
            [bootstrap :as bs]

            [harja.tiedot.navigaatio :as nav]

            [harja.views.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.views.urakka.laadunseuranta.havainnot :as havainnot]
            ))

(defonce valittu-valilehti (atom :havainnot))


(defn laadunseuranta []
  [bs/tabs
   {:active valittu-valilehti}
   
   "Tarkastukset" :tarkastukset
   [tarkastukset/tarkastukset]

   "Havainnot" :havainnot 
   [havainnot/havainnot]

   "Reklamaatiot ja sanktiot" :sanktiot
   [:div "tänne listaus sanktioista"]
   ])

