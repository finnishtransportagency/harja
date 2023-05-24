(ns harja.kyselyt.tyomaapaivakirja
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tyomaapaivakirja.sql"
  {:positional? false})
