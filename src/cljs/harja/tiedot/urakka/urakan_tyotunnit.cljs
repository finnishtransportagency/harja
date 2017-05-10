(ns harja.tiedot.urakka.urakan-tyotunnit
  "Urakan toimenpiteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit]

            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn hae-urakan-tyotunnit [urakka-id]
  (k/post! :hae-urakan-tyotunnit {::urakan-tyotunnit/urakka-id urakka-id}))


