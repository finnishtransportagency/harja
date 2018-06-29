(ns harja.kyselyt.tietyoilmoituksen-email
  (:require [specql.core :as specql]
            [specql.op :as op]
            [specql.rel :as rel]

            [harja.domain.tietyoilmoituksen-email :as e]
            [harja.kyselyt.konversio :as konv]))

(defn tallenna-lahetetyn-emailin-tiedot [db tiedot]
  (println "-----> TIEDOT:" (pr-str tiedot))
  (specql/insert! db ::e/email-lahetys tiedot))

(defn paivita-lahetetyn-emailin-tietoja [db tiedot where]
  (println "-----> TIEDOT:" (pr-str tiedot))
  (specql/update! db ::e/email-lahetys tiedot where))
