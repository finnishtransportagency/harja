(ns harja.kyselyt.koodistot
  "Koodistoihin liittyvät tietokantakyselyt
  (hetkellä vain koodisto konversiot käytetty integraatioissa)"
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/koodistot.sql"
            {:positional? false})

(defn konversio-db [db koodisto-id lahde]
  (let [rivi (first (hae-koodi-harja-koodin-perusteella db {:koodisto_id koodisto-id :lahde (str lahde)}))
        tulos (:tulos rivi)]
    (when (empty? tulos) (log/error (str "Harja koodi '" lahde "' ei voi konvertoida taulukossa " koodisto-id ". Vaatii uuden koodin lisäämistä kantaan tai selvittelyä Velholaisten kanssa!")))
    tulos))

(def konversio (memoize konversio-db))
