(ns harja.asiakas.main
  (:require [harja.asiakas.ymparisto :as ymparisto]
            [harja.views.main :as main-view]
            [harja.asiakas.tapahtumat :as t]

            ;; Tässä voidaan vaatia tiedonhallinnan juttuja, jotka kytkeytyvät app eventeihin
            ;; ja hakevat tietoa tarpeen mukaan
            [harja.tiedot.hallintayksikot :as hal]
            
            [reagent.core :as reagent]))

(defn render []
  (reagent/render-component [main-view/main] (.getElementById js/document "app")))

(defn ^:export harja []
  (ymparisto/alusta {:on-reload render})
  (render)
  
  (t/julkaise! {:aihe :harja-ladattu})
  (aset js/window "HARJA_LADATTU" true))


