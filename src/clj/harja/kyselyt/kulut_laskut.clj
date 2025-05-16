(ns harja.kyselyt.kulut-laskut
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kulut_laskut.sql"
  {:positional? false})
