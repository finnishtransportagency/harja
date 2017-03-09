(ns harja.kyselyt.yllapito-muut-toteumat
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/yllapito_toteumat.sql"
  {:positional? true})
