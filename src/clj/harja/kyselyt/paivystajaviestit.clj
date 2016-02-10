(ns harja.kyselyt.paivystajaviestit
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paivystajaviestit.sql")

(defn hae-seuraava-viestinumero[db yhteyshenkilo-id]
  (:viestinumero (first (harja.kyselyt.paivystajaviestit/hae-seuraa-vapaa-viestinumero db yhteyshenkilo-id))))