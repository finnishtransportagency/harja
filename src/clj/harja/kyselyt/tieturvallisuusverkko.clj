(ns harja.kyselyt.tieturvallisuusverkko
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/kyselyt/tieturvallisuusverkko.sql"
  {:positional? true})


