(ns harja.domain.urakan-tyotunnit
  "Urakan työtuntien skeemat."
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [harja.domain.specql-db :refer [db]]))

(define-tables
  ["urakan_tyotunnit" ::urakan-tyotunnit])
