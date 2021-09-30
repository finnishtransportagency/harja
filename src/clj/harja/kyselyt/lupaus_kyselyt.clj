(ns harja.kyselyt.lupaus-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defn muunna-lupaus [lupaus]
  (update lupaus :kirjaus-kkt konv/pgarray->vector))

(defqueries "harja/kyselyt/lupaus_kyselyt.sql")