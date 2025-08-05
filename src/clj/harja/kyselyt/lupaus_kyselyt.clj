(ns harja.kyselyt.lupaus-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(declare hae-kuukausittaiset-pisteet hae-sitoutumistiedot)

(defn muunna-lupaus [lupaus]
  (update lupaus :kirjaus-kkt konv/pgarray->vector))

(defqueries "harja/kyselyt/lupaus_kyselyt.sql")

(declare hae-urakan-lupaustiedot hae-lupaus-vaihtoehdot hae-indeksikorotus-summalle)
