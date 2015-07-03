(ns harja.kyselyt.hankkeet
  "Hankkeisiin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/hankkeet.sql")

(defn onko-tuotu-samposta? [db sampo-id]
  (:exists (first (harja.kyselyt.hankkeet/onko-tuotu-samposta db sampo-id))))