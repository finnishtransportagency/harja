(ns harja.kyselyt.specql-db
  (:require [specql.core :as specql]
            [harja.domain.muokkaustiedot]
            [harja.tyokalut.env :as env]))

(defmacro define-tables [& tables]
  ;; SpecQL lukee taulumetatiedot käännösaikana; migroitu harja-kanta riittää ilman testidataa.
  `(specql/define-tables
     {:connection-uri ~(str "jdbc:postgresql://" (env/env "HARJA_TIETOKANTA_HOST_KAANNOS" "localhost") "/harja?user=postgres")}
     ~@tables))

