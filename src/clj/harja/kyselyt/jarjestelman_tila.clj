(ns harja.kyselyt.jarjestelman-tila
  (:require [clojure.string :as string]
            [harja.tyokalut.versio :as versio]
            [jeesql.core :refer [defqueries]]))

(declare hae-jarjestelman-tila)

(defqueries "harja/kyselyt/jarjestelman_tila.sql")

(defn itmfn-tila
  ([db] (itmfn-tila db false))
  ([db kehitysmoodi?]
   (hae-jarjestelman-tila db {:kehitys? kehitysmoodi? :osa-alue "itmf"
                              ;; Klusteri saavuttaa deploymentin jälkeen tilan, jossa jokaisella palvelimella on sama versio.
                              ;; Suodatetaan pois palvelimet, jotka eivät ole samassa versiossa kuin tämä palvelin.
                              :palvelimen-versio versio/palvelimen-versio})))
