(ns harja.kyselyt.kustannusten-seuranta
  "Toteumien ja toteuman reittien kyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kustannusten_seuranta.sql"
  {:positional? true})

(declare listaa-kustannukset-paaryhmittain)

