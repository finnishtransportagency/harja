(ns harja.domain.organisaatio
  "Määrittelee organisaation nimiavaruuden specit"
  #?@(:clj  [
             (:require [clojure.spec.alpha :as s]
                       [harja.kyselyt.specql-db :refer [define-tables]]
                       )]
      :cljs [(:require [clojure.spec.alpha :as s]
               [specql.impl.registry]
               [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [define-tables]])]))

(define-tables
  ["organisaatio" ::organisaatio
   {"sampo_ely_hash" ::sampo-ely-hash
    "harjassa_luotu" ::harjassa-luotu?
    "ulkoinen_id" ::ulkoinen-id
    "luoja" ::luoja-id
    "muokkaaja" ::muokkaaja-id}])

(def urakoitsijan-perustiedot
  #{::id ::nimi ::ytunnus})

(defn organisaatio-idlla [id organisaatiot]
  (first (filter #(= (::id %) id) organisaatiot)))

;; Haut

(s/def ::vesivaylaurakoitsijat-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::ytunnus ::katuosoite ::postinumero ::postitoimipaikka])))

;; Tallennus

(s/def ::tallenna-urakoitsija-kysely
  (s/keys :req [::nimi ::ytunnus]
          :opt [::id ::katuosoite
                ::postinumero ::postitoimipaikka]))

(s/def ::tallenna-urakoitsija-vastaus
  (s/keys :req [::id ::nimi ::ytunnus
                ::katuosoite ::postinumero ::postitoimipaikka]))
