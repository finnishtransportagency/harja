(ns harja.tiedot.urakka.urakan-toimenpiteet
  "Urakan toimenpiteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.tapahtumat :as t]

            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-toimenpiteet-ja-tehtavat
  "Hakee urakan toimenpiteet (3. taso) ja tehtävät (4. taso) urakan id:llä."
  [urakka-id]
  (k/post! :urakan-toimenpiteet-ja-tehtavat urakka-id))

(defn hae-urakan-toimenpiteet
  "Hakee urakan toimenpiteet (3. taso) urakan id:llä."
  [urakka-id]
  (k/post! :urakan-toimenpiteet urakka-id))
