(ns harja.kyselyt.laskutusyhteenveto
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/laskutusyhteenveto.sql"
            {:positional? true})
