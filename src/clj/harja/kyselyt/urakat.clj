(ns harja.kyselyt.urakat
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/urakat.sql")

(defn onko-olemassa? [db id]
  (:exists (first (harja.kyselyt.urakat/onko-olemassa db id))))

(defn onko-urakalla-tehtavaa? [db urakka-id tehtava-id]
  (:exists (first (harja.kyselyt.urakat/onko-urakalla-tehtavaa db urakka-id tehtava-id))))