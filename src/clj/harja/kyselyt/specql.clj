(ns harja.kyselyt.specql
  "Määritellään yleisiä clojure.spec tyyppejä."
  (:require [specql.core :refer [define-tables]]
            [specql.data-types :as d]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.urakka :as urakka]
            [clojure.spec :as s]
            [clojure.future :refer :all]
            [clojure.string :refer [trim]]))


(def db {:connection-uri (trim (slurp ".specql-db"))})

(s/def ::d/geometry any?)

(define-tables db
  ["tr_osoite" ::tr/osoite]
  ["urakkatyyppi" ::urakka/urakkatyyppi]
  )
