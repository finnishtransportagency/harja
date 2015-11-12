(ns harja.kyselyt.hoitoluokat
  "Havaintoihin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/kyselyt/hoitoluokat.sql")