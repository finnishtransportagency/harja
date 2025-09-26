(ns harja.kyselyt.lupaus-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(declare hae-kuukausittaiset-pisteet hae-sitoutumistiedot)

;; Tämä on käytössä, vaikka kondo ei sitä huomaakaan. Sitä käytetään jeesql:ssä.
(defn muunna-lupaus [lupaus]
  (-> lupaus
    (update :kirjaus-kkt konv/pgarray->vector)
    (update :paatos-kk konv/pgarray->vector)))

(defqueries "harja/kyselyt/lupaus_kyselyt.sql")

(declare hae-urakan-lupaustiedot hae-lupaus-vaihtoehdot hae-indeksikorotus-summalle
  hae-kaynnissa-olevat-lupaus-urakat tallenna-lopputilanne! hae-urakan-lupaukset
  hae-lupauksen-kaikki-kustannusennusteet paivita-kustannusennuste-lopulliset-pisteet!)
