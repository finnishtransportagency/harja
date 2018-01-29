(ns harja.kyselyt.kanavat.kanavasillat
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch insert! update!] :as specql]))


(defqueries "harja/kyselyt/kanavat/kanavasillat.sql")

