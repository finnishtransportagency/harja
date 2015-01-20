(ns harja.asiakas.main
  (:require [harja.asiakas.ymparisto :as ymparisto]
            [harja.views.main :as main-view]
            [harja.asiakas.tapahtumat :as t]

            ;; Tässä voidaan vaatia tiedonhallinnan juttuja, jotka kytkeytyvät app eventeihin
            ;; ja hakevat tietoa tarpeen mukaan
            [harja.tiedot.hallintayksikot :as hal]
            
            [reagent.core :as reagent]))

(defn render []
  (reagent/render [main-view/main] (.getElementById js/document "app")))
  
  
(defn ^:export harja []
  (ymparisto/alusta {:on-reload render})
  (render)

  ;; Jotkut komponentit haluavat body klikkauksia kuunnella
  (set! (.-onclick js/document.body)
        (fn [e]
          (t/julkaise! {:aihe :body-klikkaus
                        :tapahtuma e})))
  
  (t/julkaise! {:aihe :harja-ladattu})
  (aset js/window "HARJA_LADATTU" true))


