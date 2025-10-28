(ns harja.kyselyt.ajastetut-tehtavat-kyselyt
(:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/ajastetut_tehtavat_kyselyt.sql"
  {:positional? true})

(declare paivita-ajastetun-tehtavan-onnistuminen! paivita-viimeisin-onnistuminen!)
