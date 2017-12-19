(ns harja.domain.kanavat.kanavan-huoltokohde
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_huoltokohde" ::huoltokohde])

(def perustiedot
  #{::id
    ::nimi})

(defn fmt-huoltokohde-nimi [huoltokohde]
  (when-let [nimi (::nimi huoltokohde)]
    (-> nimi str/lower-case str/capitalize)))

(s/def ::hae-huoltokohteet-kysely (s/coll-of ::huoltokohde))