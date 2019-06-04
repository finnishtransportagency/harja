(ns harja.domain.vesivaylat.vikailmoitus
  (:require
    [clojure.spec.alpha :as s]
    [specql.rel :as rel]

    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_vikailmoitus" ::vikailmoitus
   {::toimenpide (specql.rel/has-one ::toimenpide-id
                                 :harja.domain.vesivaylat.toimenpide/toimenpide
                                 :harja.domain.vesivaylat.toimenpide/id)
    ::turvalaite (specql.rel/has-one ::turvalaite-id
                              :harja.domain.vesivaylat.turvalaite/turvalaite
                              :harja.domain.vesivaylat.turvalaite/turvalaitenro)}])

(def viittaus-idt
  #{::turvalaite-id
    ::toimenpide-id})

(def viittaukset
  #{::toimenpide
    ::turvalaite})

(def reimari-kentat
  #{::reimari-id})

(def perustiedot
  #{::reimari-id
    ::reimari-lisatiedot
    ::reimari-ilmoittaja
    ::reimari-kirjattu
    ::reimari-luoja
    ::reimari-muokattu
    ::reimari-muokkaaja})

(def kaikki-kentat
  (clojure.set/union perustiedot reimari-kentat viittaukset viittaus-idt))

(def vikakoodi->kuvaus
  {"1022541101" "Kirjattu"
   "1022541102" "Avoin"
   "1022541103" "Korjattu"
   "1022541104" "Aiheeton"
   "1022541105" "Peruttu"})
