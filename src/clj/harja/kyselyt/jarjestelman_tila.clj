(ns harja.kyselyt.jarjestelman-tila
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/jarjestelman_tila.sql")

(defn sonjan-tila
  ([db] (sonjan-tila db false))
  ([db kehitysmoodi?]
   (hae-jarjestelman-tila db {:kehitys? kehitysmoodi? :osa-alue "sonja"})))
