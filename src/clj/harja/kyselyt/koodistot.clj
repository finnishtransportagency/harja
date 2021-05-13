(ns harja.kyselyt.koodistot
  "Koodistoihin liittyvät tietokantakyselyt
  (hetkellä vain koodisto konversiot käytetty integraatioissa)"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/koodistot.sql"
            {:positional? false})

(defn konversio [db koodisto-id harja-koodi]
  (let [rivi (first (hae-koodi-harja-koodin-perusteella db {:koodisto_id koodisto-id :harja_koodi harja-koodi}))]
    (:koodi rivi)))
