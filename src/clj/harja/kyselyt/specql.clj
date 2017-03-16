(ns harja.kyselyt.specql
  "Määritellään yleisiä clojure.spec tyyppejä."
  (:require [specql.core :refer [define-tables]]
            [specql.data-types :as d]
            [harja.domain.tietyoilmoitukset :as t]
            [harja.domain.tierekisteri :as tr]
            [clojure.spec :as s]
            [clojure.future :refer :all]))


(def db {:connection-uri (let [{:keys [palvelin tietokanta portti kayttaja salasana]}
                               (:tietokanta (read-string (slurp "asetukset.edn")))]
                           (str "jdbc:postgresql://" palvelin ":" portti "/" tietokanta
                                "?user=" kayttaja "&password=" salasana ))})

(s/def ::d/geometry any?)

(define-tables db
  ["tr_osoite" ::tr/osoite])
