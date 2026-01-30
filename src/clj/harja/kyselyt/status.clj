(ns harja.kyselyt.status
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/status.sql")

(declare hae-tietokannan-tila aseta-komponentin-tila<! db-on-aurora hae-replikoinnin-viive-aurora
  hae-replikoinnin-viive poista-statusviestit! tarkista-kantayhteys)
