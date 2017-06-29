(ns harja.domain.vesivaylat.turvalaitekomponentti
  (:require
    [clojure.spec.alpha :as s]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_turvalaitekomponentti" ::turvalaitekomponentti])

(def kentat
  #{::id
    ::lisatiedot
    ::turvalaitenro
    ::komponentti-id
    ::sarjanumero
    ::paivitysaika
    ::luontiaika
    ::luoja
    ::muokkaaja
    ::muokattu
    ::alkupvm
    ::loppupvm
    ::valiaikainen})