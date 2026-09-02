(ns harja.kyselyt.tehtavaryhmat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tehtavaryhmat.sql"
  {:positional? true})

(declare hae-tehtavaryhma hae-tehtavaryhma-tunnisteella hae-tehtavaryhmat-joilla-tehtava-on-pakollinen
  tehtavaryhmaotsikot tehtavat-tehtavaryhmaotsikoittain)
