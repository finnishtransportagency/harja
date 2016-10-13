(ns harja.tiedot.urakka.tiemerkinnan-yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))

(defn hae-yksikkohintaiset-tyot [urakka-id]
  (k/post! :hae-tiemerkinnan-yksikkohintaiset-tyot {:urakka-id  urakka-id}))

(def yksikkohintaiset-tyot
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id nakymassa?)
                (hae-yksikkohintaiset-tyot valittu-urakka-id))))

(defn tallenna-tiemerkinnan-yksikkohintaiset-tyot [urakka-id kohteet]
  (k/post! :tallenna-tiemerkinnan-yksikkohintaiset-tyot
           {:urakka-id urakka-id
            :kohteet kohteet}))

