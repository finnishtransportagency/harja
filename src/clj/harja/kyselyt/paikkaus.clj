(ns harja.kyselyt.paikkaus
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert!]]
            [harja.domain.paikkaus :as paikkaus]))

(defqueries "harja/kyselyt/paikkaus.sql"
  {:positional? true})

(defn onko-olemassa-paikkausilmioitus? [db yllapitokohde-id]
  (:exists (first (harja.kyselyt.paikkaus/yllapitokohteella-paikkausilmoitus
                    db
                    {:yllapitokohde yllapitokohde-id}))))

(defn hae-paikkaustoteumat [db hakuehdot]
  (fetch db
         ::paikkaus/paikkaustoteuma
         paikkaus/perustiedot
         hakuehdot))

(defn hae-urakan-paikkaustoteumat [db urakka-id]
  (first (hae-paikkaustoteumat db {::paikkaus/urakka-id urakka-id})))
