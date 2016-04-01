(ns harja.kyselyt.paivystajatekstiviestit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paivystajatekstiviestit.sql"
  {:positional? true})

(defn kirjaa-uusi-viesti [db yhteyshenkilo-id ilmoitus-id]
  (:viestinumero (harja.kyselyt.paivystajatekstiviestit/kirjaa-uusi-paivystajatekstiviesti<! db yhteyshenkilo-id ilmoitus-id)))

(defn hae-ilmoitus [db yhteyshenkilo-id viestinumero]
  (first (harja.kyselyt.paivystajatekstiviestit/hae-ilmoitus-idt db yhteyshenkilo-id viestinumero)))
