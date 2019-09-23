(ns harja.kyselyt.aliurakoitsijat
  "Nimiavaruutta käytetään vain urakkatyypissä teiden-hoito (MHU)."
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/aliurakoitsijat.sql"
  {:positional? false})
