(ns harja.kyselyt.vemtr
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]))

(defqueries "harja/kyselyt/vemtr.sql"
  {:positional? true})
