(ns harja.kyselyt.laskutusyhteenveto
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/laskutusyhteenveto.sql"
  {:positional? true})
