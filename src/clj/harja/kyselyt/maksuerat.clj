(ns harja.kyselyt.maksuerat
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.urakat :as urakat-q]))

(defqueries "harja/kyselyt/maksuerat.sql"
  {:positional? true})

(declare hae-kanavaurakan-maksuerien-summat hae-teiden-hoidon-urakan-maksuerien-summat
  hae-hoitourakan-maksuerien-summat hae-teiden-hoidon-urakan-maksuerat hae-hoitourakan-maksuerat
  merkitse-tyypin-maksuerat-likaisiksi!
  luo-maksuera<! merkitse-toimenpiteen-maksuerat-likaisiksi! hae-lahetettava-maksuera hae-maksueranumero-lahetys-idlla
  lukitse-maksuera! merkitse-maksuera-odottamaan-vastausta! merkitse-maksueralle-lahetysvirhe!
  merkitse-maksuera-lahetetyksi! onko-olemassa? hae-likaiset-maksuerat maksuearat-ilman-kustannussuunnitelmaa
  hae-maksueran-urakka hae-urakan-maksueratiedot)

(defn hae-urakan-maksueran-summat [db urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "vesivayla-kanavien-hoito" (hae-kanavaurakan-maksuerien-summat db urakka-id)
      "vesivayla-kanavien-korjaus" (hae-kanavaurakan-maksuerien-summat db urakka-id)
      "teiden-hoito" (hae-teiden-hoidon-urakan-maksuerien-summat db urakka-id)
      (hae-hoitourakan-maksuerien-summat db urakka-id))))

(defn hae-urakan-maksuerat [db urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "teiden-hoito" (hae-teiden-hoidon-urakan-maksuerat db urakka-id)
      (hae-hoitourakan-maksuerat db urakka-id))))
