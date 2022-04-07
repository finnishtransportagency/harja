(ns harja.kyselyt.budjettisuunnittelu
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/budjettisuunnittelu.sql"
  {:positional? false})

(defn budjettitavoite-vuodelle [db urakka-id hoitokauden-alkuvuosi]
  (->>
    (hae-budjettitavoite db {:urakka urakka-id})
    (filter #(= hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi %)))
    first))
