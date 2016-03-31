(ns harja.kyselyt.hankkeet
  "Hankkeisiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/hankkeet.sql"
  {:positional? true})

(defn onko-tuotu-samposta? [db sampo-id]
  (:exists (first (harja.kyselyt.hankkeet/onko-tuotu-samposta db sampo-id))))
