(ns harja.kyselyt.kustannussuunnitelmat
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.urakat :as urakat-q]))

(defqueries "harja/kyselyt/kustannussuunnitelmat.sql"
  {:positional? true})

(declare tuotenumero-loytyy hae-urakka-maksueranumerolla hae-kanavaurakan-kustannussuunnitelman-yksikkohintaiset-summat
  hae-hoitourakan-kustannussuunnitelman-yksikkohintaiset-summat luo-kustannussuunnitelma<!
  merkitse-toimenpiteen-kustannussunnitelmat-likaisiksi!)

(defn tuotenumero-loytyy? [db maksueranumero]
  (:exists (first (tuotenumero-loytyy db maksueranumero))))

(defn hae-kustannussuunnitelman-yksikkohintaiset-summat [db numero]
  (let [urakka-id (:id (first (hae-urakka-maksueranumerolla db numero)))
        urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "vesivayla-kanavien-hoito" (hae-kanavaurakan-kustannussuunnitelman-yksikkohintaiset-summat db numero)
      "vesivayla-kanavien-korjaus" (hae-kanavaurakan-kustannussuunnitelman-yksikkohintaiset-summat db numero)
      "hoito" (hae-hoitourakan-kustannussuunnitelman-yksikkohintaiset-summat db numero))))
