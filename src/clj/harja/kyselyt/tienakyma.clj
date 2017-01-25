(ns harja.kyselyt.tienakyma
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]))

(def muunna-toteuma
  (comp geo/muunna-reitti konv/alaviiva->rakenne))

(defqueries "harja/kyselyt/tienakyma.sql")
