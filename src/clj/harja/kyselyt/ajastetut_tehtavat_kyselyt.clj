(ns harja.kyselyt.ajastetut-tehtavat-kyselyt
(:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/ajastetut_tehtavat_kyselyt.sql"
  {:positional? true})

(declare lisaa_ajastettu_tehtava! hae-viimeisin-onnistunut-ajokerta)
