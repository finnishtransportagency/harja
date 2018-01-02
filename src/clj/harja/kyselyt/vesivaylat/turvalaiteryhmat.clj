(ns harja.kyselyt.vesivaylat.turvalaiteryhmat
            (:require [jeesql.core :refer [defqueries]]
              [specql.core :refer [fetch insert! update!] :as specql]))

(defqueries "harja/kyselyt/vesivaylat/turvalaiteryhmat.sql")

