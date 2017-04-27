(ns harja.kyselyt.organisaatiot
  "Organisaatioihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [define-tables fetch]]
            [harja.domain.organisaatio :as o]
            [harja.domain.specql-db :refer [db]]))

(defqueries "harja/kyselyt/organisaatiot.sql"
  {:positional? true})
