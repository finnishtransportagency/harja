(ns harja.asiakas.main
  (:require [harja.asiakas.ymparisto :as ymparisto]
            [harja.views.main :as main-view]
            [reagent.core :as reagent]))

(defn render []
  (reagent/render-component [main-view/main] (.getElementById js/document "app")))

(defn ^:export harja []
  (ymparisto/alusta {:on-reload render})
  (render)
  (aset js/window "HARJA_LADATTU" true))


