(ns harja.kyselyt.urakat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/urakat.sql"
  {:positional? true})

(declare urakan-paasopimus-id hae-urakka hae-urakan-tiedot hae-urakan-tyyppi hae-urakan-sopimukset
  hae-urakan-sampo-id hae-yksittainen-urakka hae-urakan-ely hae-urakan-parametrit aseta-tai-paivita-urakkaparametrit
  hae-urakat-tyypilla-ja-hallintayksikolla urakan-hallintayksikko hae-id-sampoidlla aseta-urakan-toimenkuvat
  hae-urakan-alkuvuosi onko-olemassa onko-urakalla-tehtavaa)

(defn onko-olemassa? [db id]
  (:exists (first (onko-olemassa db id))))

(defn onko-urakalla-tehtavaa? [db urakka-id tehtava-id]
  (:exists (first (onko-urakalla-tehtavaa db urakka-id tehtava-id))))
