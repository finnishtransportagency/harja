(ns harja.domain.organisaatio
  "Määrittelee organisaation nimiavaruuden specit"
  #?@(:clj [(:require [clojure.spec :as s]
                      [harja.domain.specql-db :refer [db]]
                      [specql.core :refer [define-tables]]
                      [clojure.future :refer :all])]
      :cljs [(:require [clojure.spec :as s]
                       [specql.impl.registry]
                       [specql.data-types]
                       [harja.domain.specql-db :refer [db]])
             (:require-macros
              [specql.core :refer [define-tables]])]))

(define-tables db
  ["organisaatio" ::organisaatio
   {"sampo_ely_hash" ::sampo-ely-hash
    "harjassa_luotu" ::harjassa-luotu?
    "ulkoinen_id" ::ulkoinen-id
    "luoja" ::luoja-id
    "muokkaaja" ::muokkaaja-id}])

;; Haut

(s/def ::vesivayla-urakoitsijat-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::ytunnus ::katuosoite ::postinumero])))

;; Tallennus

(s/def ::tallenna-urakoitsija-kysely (s/keys :req [::nimi ::ytunnus]
                                             :opt [::id ::katuosoite ::postinumero]))

(s/def ::tallenna-urakoitsija-vastaus (s/keys :req [::id ::nimi ::ytunnus ::katuosoite ::postinumero]))
