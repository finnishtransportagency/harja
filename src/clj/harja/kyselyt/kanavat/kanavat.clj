(ns harja.kyselyt.kanavat.kanavat
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch insert! update!] :as specql]))


(defqueries "harja/kyselyt/kanavat/kanavat.sql")


