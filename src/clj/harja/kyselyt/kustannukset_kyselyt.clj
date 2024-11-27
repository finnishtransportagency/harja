(ns harja.kyselyt.kustannukset-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kustannukset_kyselyt.sql"
  {:positional? false})

(declare hae-paikkaus-kustannukset hae-mpu-selitteet
  tallenna-mpu-kustannus! )
