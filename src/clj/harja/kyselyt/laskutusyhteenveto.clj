(ns harja.kyselyt.laskutusyhteenveto
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/laskutusyhteenveto.sql")