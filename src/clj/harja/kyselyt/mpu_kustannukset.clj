(ns harja.kyselyt.mpu-kustannukset
  (:require [jeesql.core :refer [defqueries]]))
  
  (defqueries "harja/kyselyt/mpu_kustannukset.sql"
    {:positional? false}) 
