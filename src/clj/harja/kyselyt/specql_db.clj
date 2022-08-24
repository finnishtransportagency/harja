(ns harja.kyselyt.specql-db
  (:require [specql.core :as specql]
            [specql.transform :as xf]
            [specql.rel :as rel]
            [harja.domain.muokkaustiedot]
            [harja.tyokalut.env :as env]))

(defmacro define-tables [& tables]
  `(specql/define-tables
     {:connection-uri ~(str "jdbc:postgresql://" (env/env "HARJA_TIETOKANTA_HOST_KAANNOS" "localhost") "/harjatest_template?user=postgres")}
     ~@tables))

