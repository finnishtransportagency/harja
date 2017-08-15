(ns harja.domain.vesivaylat.tyo
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [harja.domain.muokkaustiedot :as m]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["vv_tyo" ::tyo
   m/muokkaus-ja-poistotiedot
   {#?@(:clj [::hinnoittelu (rel/has-one
                              ::hinnoittelu-id
                              :harja.domain.vesivaylat.hinnoittelu/hinnoittelu
                              :harja.domain.vesivaylat.hinnoittelu/id)
              ::toimenpidekoodi (rel/has-one
                                  ::toimenpidekoodi-id
                                  :harja.domain.toimenpidekoodi/toimenpidekoodi
                                  :harja.domain.toimenpidekoodi/id)])}])

;; Löysennetään tyyppejä numeroiksi, koska JS-maailmassa ei ole BigDeccejä
(s/def ::maara number?)

(def perustiedot
  #{::id
    ::maara})

(def viittaus-idt
  #{::hinnoittelu-id
    ::toimenpidekoodi})