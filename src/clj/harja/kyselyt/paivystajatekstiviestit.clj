(ns harja.kyselyt.paivystajatekstiviestit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/paivystajatekstiviestit.sql"
  {:positional? true})

(defn kirjaa-uusi-viesti [db yhteyshenkilo-id ilmoitus-id puhelinnumero]
  (:viestinumero (harja.kyselyt.paivystajatekstiviestit/kirjaa-uusi-paivystajatekstiviesti<! db yhteyshenkilo-id ilmoitus-id puhelinnumero)))
