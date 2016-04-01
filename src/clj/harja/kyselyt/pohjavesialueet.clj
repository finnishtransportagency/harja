(ns harja.kyselyt.pohjavesialueet
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/pohjavesialueet.sql"
  {:positional? true})
