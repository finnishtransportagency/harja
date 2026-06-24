(ns harja.kyselyt.yllapito-muut-toteumat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yllapito_toteumat.sql"
  {:positional? true})

(declare luo-uusi-urakan_laskentakohde<! paivita-muu-tyo<! luo-uusi-muu-tyo<!)
