(ns harja.kyselyt.talvihoitoreitit
  "Talvihoitoreitteihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [define-tables fetch]]))

(defqueries "harja/kyselyt/talvihoitoreitit.sql"
  {:positional? true})
