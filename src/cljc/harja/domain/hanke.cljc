(ns harja.domain.hanke
  "Määrittelee hankkeen specit"
  #?@(:clj [(:require [clojure.spec.alpha :as s]
                      [harja.kyselyt.specql-db :refer [define-tables]]
                      [harja.domain.urakka :as u]
                      )]
      :cljs [(:require [clojure.spec.alpha :as s]
               [specql.impl.registry]
               [harja.domain.urakka :as u]
               [specql.data-types])
             (:require-macros
              [harja.kyselyt.specql-db :refer [define-tables]])]))

(define-tables
  ["hanke" ::hanke
   {"harjassa_luotu" ::harjassa-luotu?
    "luoja" ::luoja-id
    "muokkaaja" ::muokkaaja-id}])

;; Haut

(s/def ::hae-harjassa-luodut-hankkeet-vastaus
  (s/coll-of (s/keys :req [::id ::alkupvm ::loppupvm ::nimi]
                     :opt [::u/urakka])))

;; Tallennus

(s/def ::tallenna-hanke-kysely (s/keys :req [::alkupvm ::loppupvm ::nimi]
                                       :opt [::id]))

(s/def ::tallenna-hanke-vastaus (s/keys :req [::id ::alkupvm ::loppupvm ::nimi]))
