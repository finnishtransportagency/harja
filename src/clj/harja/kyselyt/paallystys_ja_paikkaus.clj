(ns harja.kyselyt.paallystys-ja-paikkaus
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paallystys_ja_paikkaus.sql"
  {:positional? true})
