(ns harja.kyselyt.kustannussuunnitelmat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kustannussuunnitelmat.sql"
  {:positional? true})

(defn tuotenumero-loytyy? [db maksueranumero]
  (:exists (first (harja.kyselyt.kustannussuunnitelmat/tuotenumero-loytyy db maksueranumero))))