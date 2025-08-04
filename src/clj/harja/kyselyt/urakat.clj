(ns harja.kyselyt.urakat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/urakat.sql"
  {:positional? true})

(declare urakan-paasopimus-id hae-urakka hae-urakan-tiedot hae-urakan-tyyppi hae-urakan-sopimukset
  hae-urakan-sampo-id hae-yksittainen-urakka hae-urakan-ely hae-urakan-parametrit aseta-tai-paivita-urakkaparametrit
  hae-urakat-tyypilla-ja-hallintayksikolla urakan-hallintayksikko hae-id-sampoidlla)

(defn onko-olemassa? [db id]
  (:exists (first (harja.kyselyt.urakat/onko-olemassa db id))))

(defn onko-urakalla-tehtavaa? [db urakka-id tehtava-id]
  (:exists (first (harja.kyselyt.urakat/onko-urakalla-tehtavaa db urakka-id tehtava-id))))
