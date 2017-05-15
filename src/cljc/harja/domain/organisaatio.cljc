(ns harja.domain.organisaatio
  "Määrittelee organisaation nimiavaruuden specit"
  (:require
    [clojure.spec.alpha :as s]
    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["organisaatio" ::organisaatio
   {"sampo_ely_hash" ::sampo-ely-hash
    "harjassa_luotu" ::harjassa-luotu?
    "ulkoinen_id" ::ulkoinen-id
    "luoja" ::luoja-id
    "muokkaaja" ::muokkaaja-id}])

(def urakoitsijan-perustiedot
  #{::id ::nimi ::ytunnus})

;; Haut

(s/def ::vesivayla-urakoitsijat-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::ytunnus ::katuosoite ::postinumero])))

;; Tallennus

(s/def ::tallenna-urakoitsija-kysely (s/keys :req [::nimi ::ytunnus]
                                             :opt [::id ::katuosoite ::postinumero]))

(s/def ::tallenna-urakoitsija-vastaus (s/keys :req [::id ::nimi ::ytunnus ::katuosoite ::postinumero]))
