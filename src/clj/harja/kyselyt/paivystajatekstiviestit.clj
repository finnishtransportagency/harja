(ns harja.kyselyt.paivystajatekstiviestit
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paivystajaviestit.sql")

(defn hae-seuraava-viestinumero[db yhteyshenkilo-id]
  (:viestinumero (first (harja.kyselyt.paivystajatekstiviestit/hae-seuraa-vapaa-viestinumero db yhteyshenkilo-id))))