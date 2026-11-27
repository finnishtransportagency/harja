(ns harja.kyselyt.toteutuneet-toimenpiteet-kyselyt
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toteutuneet_toimenpiteet_kyselyt.sql"
  {:positional? false})

(declare hae-vhu-toteutuneet-toimenpiteet)
