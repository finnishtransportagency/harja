(ns harja.kyselyt.specql-db
  (:require [specql.core :as specql]
            [specql.transform :as xf]
            [specql.rel :as rel]
            [harja.domain.muokkaustiedot]))

(defmethod specql.impl.composite/parse-value :specql.data-types/int4 [_ string]
  (println "jeejee ")(Long/parseLong string))
(defmacro define-tables [& tables]
  `(specql/define-tables
     {:connection-uri "jdbc:postgresql://localhost/harjatest_template?user=harja"}
     ~@tables))
