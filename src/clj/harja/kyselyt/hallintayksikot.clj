(ns harja.kyselyt.hallintayksikot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/hallintayksikot.sql"
  {:positional? true})

(declare hae-organisaatio listaa-hallintayksikot-kulkumuodolle listaa-elinvoimakeskukset-kulkumuodolle
  hae-organisaation-tunnistetiedot hallintayksikot-ilman-geometriaa)
