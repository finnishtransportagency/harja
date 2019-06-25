(ns harja.domain.hairioilmoitus
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.set :as set]
            [specql.rel :as rel]
            [harja.domain.muokkaustiedot :as m]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            ])
            [harja.pvm :as pvm])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["hairioilmoitus_tyyppi" ::hairioilmoitus-tyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["hairioilmoitus" ::hairioilmoitus
   {"voimassa" ::voimassa?}])

(def tyyppi-fmt
  {:hairio "Häiriöilmoitus"
   :tiedote "Tiedote"})

(def sarakkeet #{::id ::viesti ::pvm ::voimassa? ::tyyppi})

(defn tuorein-voimassaoleva-hairio [hairiot]
  (->> hairiot
       (filter #(true? (::voimassa? %)))
       (sort-by ::pvm)
       (first)))
