(ns harja.kyselyt.liitteet
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/liitteet.sql"
  {:positional? true})

(declare hae-liitteiden-tiedot hae-urakan-liite-id hae-liite-meta-tiedoilla)
