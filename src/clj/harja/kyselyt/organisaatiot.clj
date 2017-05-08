(ns harja.kyselyt.organisaatiot
  "Organisaatioihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [define-tables fetch]]
            [harja.domain.organisaatio :as o]))

(defqueries "harja/kyselyt/organisaatiot.sql"
  {:positional? true})
