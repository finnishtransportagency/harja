(ns harja.kyselyt.yllapito-kustannukset-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yllapito_kustannukset_kyselyt.sql"
  {:positional? false})

(declare hae-paikkaus-kustannukset hae-kustannusten-selitteet
  tallenna-yllapito-kustannus! )
