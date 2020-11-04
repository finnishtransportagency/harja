(ns harja.kyselyt.specql
  "Määritellään yleisiä clojure.spec tyyppejä."
  (:require [specql.data-types :as d]
            [specql.transform :as tx]
            [harja.domain.tierekisteri :as tr]
            [clojure.spec.alpha :as s]
            [harja.geo :as geo]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:clj
     (:import (org.postgis PGgeometry)
              (java.util UUID))))

(s/def ::d/geometry any?)

(define-tables
  ["tr_osoite" ::tr/osoite])

;; TODO ALLA OLEVAT MÄÄRITYKSET PITÄISI TEHDÄ OIKEASTI SPECQL-KIRJASTOON
;; Kun tehty, voi myös harja.domain.liitteeltä poistaa require tähän ns:ään.

#?(:clj
   (defmethod specql.impl.composite/parse-value :specql.data-types/int4 [_ string]
     (Long/parseLong string)))

#?(:clj
   (defmethod specql.impl.composite/parse-value "geometry" [_ geometria]
     (PGgeometry. geometria)))

(s/def :specql.data-types/uint4 (s/int-in 0 4294967295))
(s/def :specql.data-types/oid :specql.data-types/uint4)

(defrecord Geometry []
  tx/Transform
  (from-sql [_ geometry]
    #?(:clj (geo/pg->clj geometry)
       :cljs (identity geometry)))
  (to-sql [_ geometry]
    #?(:clj (geo/clj->pg geometry)
       :cljs (identity geometry)))
  (transform-spec [_ input-spec]
    ;; Ei osata specata geometriatyyppejä, joten spec olkoon any?
    any?))

(defrecord GeometryTierekisteri []
  tx/Transform
  (from-sql [_ geometry]
    #?(:clj (geo/pg->clj geometry)
       :cljs (identity geometry)))
  (to-sql [_ geometry]
    (identity geometry))
  (transform-spec [_ input-spec]
    ;; Ei osata specata geometriatyyppejä, joten spec olkoon any?
    any?))

(defrecord NumberTransform []
  tx/Transform
  (from-sql [_ num]
    (when num
      (float num)))
  (to-sql [_ num]
    #?(:clj (when num
              (bigdec num))
       :cljs num))
  (transform-spec [_ input-spec]
    number?))

(defrecord UUIDTransform []
  tx/Transform
  (from-sql [_ uuid]
    (str uuid))
  (to-sql [_ uuid]
    #?(:clj (UUID/fromString uuid)
       :cljs uuid))
  (transform-spec [_ input-spec]
    #?(:clj #(try (uuid? (UUID/fromString %))
                  (catch Throwable _ false))
       :cljs any?)))