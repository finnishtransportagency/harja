(ns harja.kyselyt.vesivaylat.vatu-turvalaitteet
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch insert! update!] :as specql]))



(defqueries "harja/kyselyt/vesivaylat/vatu_turvalaitteet.sql")

;;TODO: Täydennä kun turvalaiterefactorointi tehdään (vrt. kyselyt.vesivaylat.turvalaitteet)
