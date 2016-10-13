(ns harja.tiedot.urakka.tiemerkinnan-yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))

(defonce yksikkohintaiset-tyot (atom nil))

(defn tallenna-tiemerkinnan-yksikkohintaiset-tyot [urakka-id sopimus-id kohteet]
  (k/post! :tallenna-tiemerkinnan-yksikkohintaiset-tyot
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :kohteet kohteet}))

