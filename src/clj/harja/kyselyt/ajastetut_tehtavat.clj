(ns harja.kyselyt.ajastetut-tehtavat
(:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/ajastetut_tehtavat.sql"
  {:positional? true})

(declare paivita-ajastetun-tehtavan-onnistuminen! paivita-viimeisin-onnistuminen!)
