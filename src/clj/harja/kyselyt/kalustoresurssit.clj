(ns harja.kyselyt.kalustoresurssit
  "Suunnittelun kalustoresursseihin liittyvät tietokantakyselyt."
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/kalustoresurssit.sql")

(declare hae-urakan-kalustoresurssit
         tallenna-kalustoresurssi<!)
