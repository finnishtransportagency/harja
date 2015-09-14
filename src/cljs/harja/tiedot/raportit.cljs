(ns harja.tiedot.raportit
  "Raportit"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn hae-yksikkohintaisten-toiden-kuukausiraportti [urakka-id alkupvm loppupvm]
  (k/post! :yksikkohintaisten-toiden-kuukausiraportti
           {:urakka-id urakka-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))