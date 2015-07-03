(ns harja.kyselyt.hankkeet
  "Hankkeisiin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/hankkeet.sql")