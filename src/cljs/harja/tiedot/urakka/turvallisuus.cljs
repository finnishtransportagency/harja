(ns harja.tiedot.urakka.turvallisuus
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce turvallisuus-valilehdella? (atom false))

(defonce valittu-valilehti (atom :turvallisuuspoikkeamat))

