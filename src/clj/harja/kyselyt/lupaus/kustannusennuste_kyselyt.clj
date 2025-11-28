(ns harja.kyselyt.lupaus.kustannusennuste-kyselyt
  "Kustannusennuste-lupauksen tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/lupaus/kustannusennuste_kyselyt.sql")

(declare 
  hae-kustannusennuste-id
  hae-kustannusennuste
  lisaa-kustannusennuste<!
  paivita-kustannusennuste<!
  hae-lupauksen-kaikki-kustannusennusteet
  hae-kustannusennuste-kuukausi-pisterajat
  hae-kustannusennuste-kuukausi-offset
  hae-valikatselmuksen-vahvistetut-kustannusennusteet
  paivita-kustannusennuste-lopulliset-pisteet!
  onko-kustannusennuste-pisteet-laskettu
  hae-kustannusennuste-maarapaivat
  hae-urakat-joilla-kustannusennuste
  hae-urakan-kaikki-kustannusennusteet-testaus
  hae-poistettavien-kustannusennusteiden-lkm
  poista-urakan-hoitokauden-kustannusennusteet!
  hae-urakan-kustannusennuste-lupaus-id)
