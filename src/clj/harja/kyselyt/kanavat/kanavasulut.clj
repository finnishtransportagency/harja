(ns harja.kyselyt.kanavat.kanavasulut
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch insert! update!] :as specql]))


(defqueries "harja/kyselyt/kanavat/kanavasulut.sql")


