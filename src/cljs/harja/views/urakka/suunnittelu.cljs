(ns harja.views.urakka.suunnittelu
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            
            [harja.views.urakka.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]))


(defn suunnittelu [ur]
  
   [bs/tabs {:style :pills}

    "Yksikköhintaiset työt"
    ^{:key "yksikkohintaiset-tyot"}
    [yksikkohintaiset-tyot/yksikkohintaiset-tyot ur]

    "Kokonaishintaiset työt"
    [:div "Kokonaishintaiset työt"]
    ;;^{:key "kokonaishintaiset-tyot"}
    ;;[yht/yksikkohintaiset-tyot]
    
    "Materiaalit"
    [:div "Materiaalit"]
    ;;^{:key "materiaalit"}
])
