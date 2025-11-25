(ns harja.kyselyt.hankkeet
  "Hankkeisiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(declare onko-tuotu-samposta)

(defqueries "harja/kyselyt/hankkeet.sql"
  {:positional? true})

(declare luo-hanke<! paivita-hanke-samposta!)

(defn onko-tuotu-samposta? [db sampo-id]
  (:exists (first (onko-tuotu-samposta db sampo-id))))
