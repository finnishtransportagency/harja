(ns harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))
(def +uusi-turvallisuuspoikkeama+ {})
(defonce valittu-turvallisuuspoikkeama (atom nil))
(defonce haetut-turvallisuuspoikkeamat (atom {}))
