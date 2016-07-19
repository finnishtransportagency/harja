(ns harja.kyselyt.tilannekuva
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]))

(defn muunna-reitti [{reitti :reitti :as rivi}]
  ;(println "MUUNNA " rivi)
  (assoc rivi
         :reitti (geo/pg->clj reitti)))

(defqueries "harja/kyselyt/tilannekuva.sql"
  {:positional? true})
