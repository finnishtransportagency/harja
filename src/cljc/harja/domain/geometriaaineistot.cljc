(ns harja.domain.geometriaaineistot
  "Urakan työtuntien skeemat."
  (:require [clojure.spec.alpha :as s]
            [specql.impl.registry]
            [specql.data-types]
            [harja.pvm :as pvm]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            
            [clj-time.core :as t]])
    #?(:cljs [cljs-time.core :as t]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["geometriaaineisto" ::geometria-aineistot {}])

(def kaikki-kentat
  #{::id
    ::nimi
    ::tiedostonimi
    ::voimassaolo-alkaa
    ::voimassaolo-paattyy})

(s/def ::geometria-aineistot (s/coll-of ::geometria-aineistot))

(s/def ::geometria-aineistojen-tallennus (s/keys :req [::nimi ::tiedostonimi ::osoite]))

