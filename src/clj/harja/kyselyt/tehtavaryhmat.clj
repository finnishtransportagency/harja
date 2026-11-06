(ns harja.kyselyt.tehtavaryhmat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tehtavaryhmat.sql"
  {:positional? true})

(declare hae-tehtavaryhma hae-tehtavaryhma-tunnisteella hae-tehtavaryhmat-joilla-tehtava-on-pakollinen
  hae-mhu-tehtavaryhmaotsikot tehtavat-tehtavaryhmaotsikoittain hae-mhu-tehtavaryhmaotsikot-tehtavaryhmat-ja-tehtavat
  hae-vvh-tehtavaryhmaotsikot-tehtavaryhmat-ja-tehtavat
  paivita-tehtavaryhma! hae-tehtava paivita-tehtava!)
