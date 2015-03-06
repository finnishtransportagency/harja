(ns harja.views.urakka.yksikkohintaiset-tyot
  "Urakan 'Yksikkohintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? alasveto-ei-loydoksia alasvetovalinta radiovalinta]]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.ui.yleiset :refer [deftk]]))

(deftk yksikkohintaiset-tyot [ur]
  [tyot (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))]
  (do
    (log "urakka " (:nimi ur))
    (log "yksikkohintaiset-tyot " (pr-str tyot))
    [:div [:span.alasvedon-otsikko "Sopimusnumero"]
     [alasvetovalinta {:valinta nil
                       :format-fn #(if % (:numero %) "Valitse")
                       :valitse-fn (log "valitse-fn")
                       :class "alasveto"
                       }
      [{:numero 1} {:numero 2}]
      ]]))
