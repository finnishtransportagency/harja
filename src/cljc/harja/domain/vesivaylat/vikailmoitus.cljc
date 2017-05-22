(ns harja.domain.vesivaylat.vikailmoitus
  (:require
    [clojure.spec.alpha :as s]

    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_vikailmoitus" ::vikailmoitus
   {#?@(:clj [::toimenpide (rel/has-one ::toimenpide-id
                                        :harja.domain.vesivaylat.toimenpide/toimenpide
                                        :harja.domain.vesivaylat.toimenpide/id)
              ::turvalaite (rel/has-one ::turvalaite-id
                                        :harja.domain.vesivaylat.turvalaite/turvalaite
                                        :harja.domain.vesivaylat.turvalaite/id)])}])

(def viittaus-idt
  #{::turvalaite-id
    ::toimenpide-id})

(def viittaukset
  #{::toimenpide
    ::turvalaite})

(def reimari-kentat
  #{::reimari-id})

(def perustiedot
  #{::id
    ::kuvaus
    ::pvm})

(def kaikki-kentat
  (clojure.set/union perustiedot reimari-kentat viittaukset viittaus-idt))