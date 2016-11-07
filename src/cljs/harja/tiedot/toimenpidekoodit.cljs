(ns harja.tiedot.toimenpidekoodit
  "Toimenpidekoodien tiedot"
  (:require [reagent.core :refer [atom wrap] :as reagent]
            [cljs.core.async :refer [<!]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def koodit "id->koodi mäppäys kaikista toimenpidekoodeista" (atom nil))

(defonce koodit-tasoittain (reaction (group-by :taso (sort-by :koodi (vals @koodit)))))

(def tyokoneiden-reaaliaikaseuranna-tehtavat "työkoneiden reaaliaikaseurannan seurattavat tehtävät" (atom nil))
