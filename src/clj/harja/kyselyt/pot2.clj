(ns harja.kyselyt.pot2

  (:require [jeesql.core :refer [defqueries]]))

(def hae-kohteen-pot2-tiedot
  "Hakee kohteen pot2-tiedot. Parametrinä ylläpitokohteen id.")

(defqueries "harja/kyselyt/pot2.sql")