(ns harja.kyselyt.maksuerat
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.urakat :as urakat-q]))

(defqueries "harja/kyselyt/maksuerat.sql"
  {:positional? true})

(defn hae-urakan-maksueran-summat [db urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "vesivayla-kanavien-hoito" (harja.kyselyt.maksuerat/hae-kanavaurakan-maksuerien-summat db urakka-id)
      "vesivayla-kanavien-korjaus" (harja.kyselyt.maksuerat/hae-kanavaurakan-maksuerien-summat db urakka-id)
      "teiden-hoito" (harja.kyselyt.maksuerat/hae-teiden-hoidon-urakan-maksuerien-summat db urakka-id)
      (harja.kyselyt.maksuerat/hae-hoitourakan-maksuerien-summat db urakka-id))))
