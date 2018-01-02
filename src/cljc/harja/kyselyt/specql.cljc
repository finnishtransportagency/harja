(ns harja.kyselyt.specql
  "Määritellään yleisiä clojure.spec tyyppejä."
  (:require [specql.data-types :as d]
            [harja.domain.tierekisteri :as tr]
            [clojure.spec.alpha :as s]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
              ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:clj
     (:import (org.postgis PGgeometry))))

(s/def ::d/geometry any?)

(define-tables
  ["tr_osoite" ::tr/osoite])

;; Alla olevia tyyppimäärityksiä ei ole specql:ssä sisäänrakennettuna, -> lisätään ne käsin.

#?(:clj
   (defmethod specql.impl.composite/parse-value :specql.data-types/int4 [_ string]
     (Long/parseLong string)))

#?(:clj
   (defmethod specql.impl.composite/parse-value "geometry" [_ geometria]
     (PGgeometry. geometria)))

(s/def :specql.data-types/uint4 (s/int-in 0 4294967295))
(s/def :specql.data-types/oid :specql.data-types/uint4)