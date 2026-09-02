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
  hae-urakan-kustannusennuste-lupaus-id
  hae-lupauksen-kaikki-kustannusennusteet-kaikki-hoitovuodet)

(defn hae-lupauksen-kustannusennusteet
  "Hakee lupauksen kaikki kustannusennusteet hoitokaudelle.
   Käytetään lupaus-rikastuksessa ja lupaus-palvelussa."
  [db lupaus-id urakka-id hoitokauden-alkuvuosi]
  (when (and lupaus-id urakka-id hoitokauden-alkuvuosi)
    (hae-lupauksen-kaikki-kustannusennusteet
      db {:lupaus-id lupaus-id
          :urakka-id urakka-id
          :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))

(defn onko-kustannusennuste-pisteet-laskettu?
  "Tarkistaa onko kaikille kustannusennusteille laskettu lopulliset pisteet.
   Käytetään lupaus-rikastuksessa ja lupaus-palvelussa."
  [db urakka-id hoitokauden-alkuvuosi]
  (when (and urakka-id hoitokauden-alkuvuosi)
    (let [tulos (first (onko-kustannusennuste-pisteet-laskettu
                         db {:urakka-id urakka-id
                             :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
      {:kaikki-laskettu (:kaikki_laskettu tulos)
       :yhteensa (:yhteensa tulos)
       :laskettu-pisteet (:laskettu_pisteet tulos)})))
