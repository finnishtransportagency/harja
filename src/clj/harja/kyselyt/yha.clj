(ns harja.kyselyt.yha
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yha.sql")

(declare luo-yllapitokohdeosa<! hae-urakan-yhatiedot)
