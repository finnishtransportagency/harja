(ns harja.kyselyt.erilliskustannus-kyselyt
  "Erilliskustannuksiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/erilliskustannus_kyselyt.sql"
  {:positional? true})
