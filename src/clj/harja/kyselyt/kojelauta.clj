(ns harja.kyselyt.kojelauta
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kojelauta.sql"
            {:positional? true})

(declare hae-hoidon-urakat-kojelautaan hae-paallystysurakat-kojelautaan)
