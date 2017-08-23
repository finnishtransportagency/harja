(ns harja.domain.hairioilmoitus
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.set :as set]
            [specql.rel :as rel]
            [harja.domain.muokkaustiedot :as m]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            [clojure.future :refer :all]])
            [harja.pvm :as pvm])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["hairioilmoitus" ::hairioilmoitus
   {"voimassa" ::voimassa?}])

(def sarakkeet #{::id ::viesti ::pvm ::voimassa?})

(defn tuorein-voimassaoleva-hairio [hairiot]
  (->> hairiot
       (filter #(true? (::voimassa? %)))
       (sort-by ::pvm)
       (first)))