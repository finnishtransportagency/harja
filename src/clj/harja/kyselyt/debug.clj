(ns harja.kyselyt.debug
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/debug.sql")

(declare paivita-toteuma-tehtavat paivita-toteuma-materiaalit paivita-pohjavesialuekooste paivita-pohjavesialueiden-suolatoteumat)
