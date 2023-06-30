(ns harja.kyselyt.koodistot
  "Koodistoihin liittyvät tietokantakyselyt
  (hetkellä vain koodisto konversiot käytetty integraatioissa)"
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/koodistot.sql"
            {:positional? false})

(defn konversio-db 
  ([db hakuvirhe-fn koodisto-id lahde]
   (konversio-db db hakuvirhe-fn koodisto-id lahde nil))
  ([db hakuvirhe-fn koodisto-id lahde kohde]
   (let [rivi (first (hae-koodi-harja-koodin-perusteella db {:koodisto_id koodisto-id :lahde (str lahde)}))
         tulos (:tulos rivi)
         virhe-sanoma (str "Harja koodi '" lahde "' ei voi konvertoida taulukossa " koodisto-id ". Vaatii uuden koodin lisäämistä kantaan tai selvittelyä Velholaisten kanssa!")]
     (when (empty? tulos) 
       (hakuvirhe-fn
         db kohde
         virhe-sanoma))
     (assert (some? tulos) virhe-sanoma)
     tulos)))

(def konversio (memoize konversio-db))
