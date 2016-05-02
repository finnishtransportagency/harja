(ns harja.kyselyt.yha
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/yha.sql")
