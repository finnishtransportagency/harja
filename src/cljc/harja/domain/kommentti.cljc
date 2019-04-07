(ns harja.domain.kommentti
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.set :as set]
    [harja.pvm :as pvm]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])
    [harja.domain.urakka :as ur]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.muokkaustiedot :as muokkaustiedot])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kommentti" ::kommentti
   {"kommentti" ::kommentti-teksti
    "liite" ::liite-id}])


(defn liitteen-kommentti [kommentit liite-id]
  (first (filter #(= (get-in % [:liite :id]) liite-id)
                 kommentit)))
