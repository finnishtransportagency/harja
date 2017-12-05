(ns harja.domain.vesivaylat.sopimus
  (:require
    [clojure.spec.alpha :as s]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(def reimari-sopimustyypit
  {"1022542301" :suoritusvelvoitteinen
   "1022542302" :maksuperusteinen
   "1022542303" :laajuuden-mukainen})

(define-tables
  ["reimari_sopimus" ::reimari-sopimus
   {"nro" ::r-nro
    "nimi" ::r-nimi
    "tyyppi" ::r-tyyppi
    "diaarinro" ::r-diaarinro}])
