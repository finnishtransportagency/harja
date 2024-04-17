(ns harja.kyselyt.jarjestelman-tila
  (:require [clojure.string :as string]
            [jeesql.core :refer [defqueries]]))

(declare hae-jarjestelman-tila)

(defqueries "harja/kyselyt/jarjestelman_tila.sql")

(def palvelimen-versio (some->
                         (slurp "harja_image_id.txt")
                         (string/trim-newline)))

(defn itmfn-tila
  ([db] (itmfn-tila db false))
  ([db kehitysmoodi?]
   (hae-jarjestelman-tila db {:kehitys? kehitysmoodi? :osa-alue "itmf"
                              ;; Klusteri saavuttaa deploymentin jälkeen tilan, jossa jokaisella palvelimella on sama versio.
                              ;; Suodatetaan pois palvelimet, jotka eivät ole samassa versiossa kuin tämä palvelin.
                              :palvelimen-versio palvelimen-versio})))
