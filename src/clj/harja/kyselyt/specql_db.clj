(ns harja.kyselyt.specql-db
  (:require [specql.core :as specql]
            [specql.transform :as xf]
            [specql.rel :as rel]
            [harja.domain.muokkaustiedot]))

(defmacro define-tables [& tables]
  `(specql/define-tables
     {:connection-uri ~(str "jdbc:postgresql://" (System/getenv "HARJA_TIETOKANTA_HOST_KAANNOS") "/harjatest_template?user=postgres")}
     ~@tables))

