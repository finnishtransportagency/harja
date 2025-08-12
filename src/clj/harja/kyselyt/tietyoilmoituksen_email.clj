(ns harja.kyselyt.tietyoilmoituksen-email
  (:require [specql.core :as specql]
            [harja.domain.tietyoilmoituksen-email :as e]))

(defn tallenna-lahetetyn-emailin-tiedot [db tiedot]
  (specql/insert! db ::e/email-lahetys tiedot))

(defn paivita-lahetetyn-emailin-tietoja [db tiedot where]
  (specql/update! db ::e/email-lahetys tiedot where))
