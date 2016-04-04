(ns harja.kyselyt.muokkauslukko
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/muokkauslukko.sql"
  {:positional? true})
