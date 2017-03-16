(ns harja.kyselyt.specql
  "Määritellään yleisiä clojure.spec tyyppejä."
  (:require [specql.core :refer [define-tables]]
            [specql.data-types :as d]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [clojure.spec :as s]
            [clojure.future :refer :all]))

(def db {:connection-uri "jdbc:postgresql://localhost/harjatest_template?user=postgres"})

(s/def ::d/geometry any?)

(define-tables db
  ["tr_osoite" ::tr/osoite])
