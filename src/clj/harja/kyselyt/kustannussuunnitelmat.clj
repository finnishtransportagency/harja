(ns harja.kyselyt.kustannussuunnitelmat
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.urakat :as urakat-q]))

(defqueries "harja/kyselyt/kustannussuunnitelmat.sql"
  {:positional? true})

(defn tuotenumero-loytyy? [db maksueranumero]
  (:exists (first (harja.kyselyt.kustannussuunnitelmat/tuotenumero-loytyy db maksueranumero))))

(defn hae-kustannussuunnitelman-yksikkohintaiset-summat [db numero]
  (let [urakka-id (:id (first (harja.kyselyt.kustannussuunnitelmat/hae-urakka-maksueranumerolla db numero)))
        urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "vesivayla-kanavien-hoito" (harja.kyselyt.kustannussuunnitelmat/hae-kanavaurakan-kustannussuunnitelman-yksikkohintaiset-summat db numero)
      "vesivayla-kanavien-korjaus" (harja.kyselyt.kustannussuunnitelmat/hae-kanavaurakan-kustannussuunnitelman-yksikkohintaiset-summat db numero)
      (harja.kyselyt.kustannussuunnitelmat/hae-teiden-hoidon-kustannussuunnitelman-yksikkohintaiset-summat db numero))))
