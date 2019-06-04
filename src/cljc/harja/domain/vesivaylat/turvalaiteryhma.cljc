(ns harja.domain.vesivaylat.turvalaiteryhma
  (:require
    [clojure.set]
    [clojure.spec.alpha :as s]
    [harja.domain.vesivaylat.turvalaite :as turvalaite]
    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))



(define-tables
  ["reimari_turvalaiteryhma" ::reimari-turvalaiteryhma])

(def turvalaite #{[::turvalaite harja.domain.vesivaylat.turvalaite/perustiedot]})

(def perustiedot
  #{:tunnus
    :nimi
    :kuvaus
    :turvalaitteet
    :luoja
    :luotu
    :muokkaaja
    :muokattu
})

(s/def ::hae-turvalaiteryhmat-kysely
  (s/keys :req []))

(s/def ::hae-turvalaiteryhmat-vastaus
  (s/nilable (s/coll-of ::turvalaiteryhma)))

