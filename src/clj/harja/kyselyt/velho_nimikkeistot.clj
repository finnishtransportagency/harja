(ns harja.kyselyt.velho-nimikkeistot
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/velho_nimikkeistot.sql"
  {:positional? false})
