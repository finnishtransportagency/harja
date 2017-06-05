(ns harja.kyselyt.specql-db
  (:require [specql.core :as specql]
            [specql.transform :as xf]
            [harja.domain.muokkaustiedot]))

(defmacro define-tables [& tables]
  `(specql/define-tables
     {:connection-uri "jdbc:postgresql://localhost/harjatest_template?user=harja"}
     ~@tables))
