(ns harja.kyselyt.taitorakennerekisteri
  "Taitorakennerekisterin kyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/taitorakennerekisteri.sql"
  {:positional? true})
