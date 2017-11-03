(ns harja.domain.vesivaylat.turvalaiteryhma
  (:require
    [clojure.set]
    [clojure.spec.alpha :as s]
    [harja.domain.vesivaylat.turvalaite :as vv_turvalaite]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_turvalaiteryhma" ::turvalaiteryhma])


(def turvalaite #{[::turvalaite  vv_turvalaite/perustiedot]})

;; refac
(def viittaukset
  (clojure.set/union
    turvalaite))

(def perustiedot
  #{::ryhmanro
    ::nimi
    ::kuvaus
    ::turvalaitteet})

(defn turvalaiteryhmien-turvalaitteet [turvalaitteet]
  (distinct (map #(::turvalaite %) turvalaitteet)))

(s/def ::hae-turvalaiteryhmat-kysely
  (s/keys :req []))

(s/def ::hae-turvalaiteryhmat-vastaus
  (s/nilable (s/coll-of ::turvalaiteryhma)))

