(ns harja.kyselyt.muutos-kyselyt
  (:require [jeesql.core :refer [defqueries]]))


(defqueries "harja/kyselyt/muutos_kyselyt.sql")

(declare hae-urakan-hoitovuoden-kirjatut-muutokset rahavarausten-toteumat rahavarausmuutosten-syyt
  hae-tehtava-maaramuutokset paivita-tehtava-tiedot<! paivita-muutostyo-kulukohdistus!)
