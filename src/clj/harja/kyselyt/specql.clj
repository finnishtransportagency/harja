(ns harja.kyselyt.specql
  "Määritellään yleisiä clojure.spec.alpha tyyppejä."
  (:require [harja.kyselyt.specql-db :refer [define-tables]]
            [specql.data-types :as d]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.urakka :as urakka]
            [clojure.spec.alpha :as s]
            [clojure.future :refer :all]
            [clojure.string :refer [trim]]
            [clojure.java.io :as io]))


(s/def ::d/geometry any?)

(define-tables
  ["tr_osoite" ::tr/osoite])
