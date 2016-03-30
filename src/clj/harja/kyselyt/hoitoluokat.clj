(ns harja.kyselyt.hoitoluokat
  "Havaintoihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/kyselyt/hoitoluokat.sql"
  {:positional? true})
