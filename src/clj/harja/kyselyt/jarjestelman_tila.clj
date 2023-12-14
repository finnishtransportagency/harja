(ns harja.kyselyt.jarjestelman-tila
  (:require [jeesql.core :refer [defqueries]]))

(declare hae-jarjestelman-tila)

(defqueries "harja/kyselyt/jarjestelman_tila.sql")

(defn itmfn-tila
  ([db] (itmfn-tila db false))
  ([db kehitysmoodi?]
   (hae-jarjestelman-tila db {:kehitys? kehitysmoodi? :osa-alue "itmf"})))
