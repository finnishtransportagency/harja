(ns harja.domain.geometriaaineistot
  "Urakan työtuntien skeemat."
  (:require [clojure.spec.alpha :as s]
            [specql.impl.registry]
            [specql.data-types]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]])
    #?(:cljs [cljs-time.core :as t]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["geometriaaineisto" ::geometria-aineistot {} ]
  ["geometriapaivitys" ::geometriapaivitys])

(def geometria-aineistot-kaikki-kentat
  #{::id
    ::nimi
    ::tiedostonimi
    ::voimassaolo-alkaa
    ::voimassaolo-paattyy})

(def geometriapaivitys-kaikki-kentat
  #{::id
    ::nimi
    ::viimeisin_paivitys
    ::seuraava_paivitys
    ::edellinen_paivitysyritys
    ::paikallinen
    ::kaytossa
    ::lisatieto})

(s/def ::geometria-aineistot (s/coll-of ::geometria-aineistot))

(s/def ::geometria-aineistojen-tallennus (s/keys :req [::nimi ::tiedostonimi ::osoite]))

