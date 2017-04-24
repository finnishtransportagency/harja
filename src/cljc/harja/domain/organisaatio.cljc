(ns harja.domain.organisaatio
  "Määrittelee organisaation nimiavaruuden specit"
  #?@(:clj [(:require [clojure.spec :as s]
                      [harja.kyselyt.specql-db :refer [db]]
                      [specql.core :refer [define-tables]]
                      [clojure.future :refer :all])]
      :cljs [(:require [clojure.spec :as s]
                       [specql.impl.registry]
                       [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [db]]
               [specql.core :refer [define-tables]])]))

;; TODO Tämä on generoitu käyttäen macroa:
;; (macroexpand '(define-tables db ["organisaatio" ::organisaatio
;; {"sampo_ely_hash" ::sampo-ely-hash
;;  "harjassa_luotu" ::harjassa-luotu?
;;  "ulkoinen_id" ::ulkoinen-id
;;  "luoja" ::luoja-id
;;  "muokkaaja" ::muokkaaja-id}]))
;; Jouduttiin expandoimaan käsin, koska figwheel / phantom ei osannut käsitellä makroa sellaisenaan.
;; Vaatii pohtimista miten ratkaistaan.
(do
  (clojure.core/swap!
    specql.impl.registry/table-info-registry
    clojure.core/merge
    {:harja.domain.organisaatio/organisaatio {:name "organisaatio",
                                              :type :table,
                                              :columns {:harja.domain.organisaatio/luoja-id {:name "luoja",
                                                                                             :number 14,
                                                                                             :not-null? false,
                                                                                             :has-default? false,
                                                                                             :type-specific-data -1,
                                                                                             :type "int4",
                                                                                             :category "N",
                                                                                             :primary-key? false,
                                                                                             :enum? false},
                                                        :harja.domain.organisaatio/id {:name "id",
                                                                                       :number 1,
                                                                                       :not-null? true,
                                                                                       :has-default? true,
                                                                                       :type-specific-data -1,
                                                                                       :type "int4",
                                                                                       :category "N",
                                                                                       :primary-key? true,
                                                                                       :enum? false},
                                                        :harja.domain.organisaatio/sampo-ely-hash {:name "sampo_ely_hash",
                                                                                                   :number 13,
                                                                                                   :not-null? false,
                                                                                                   :has-default? false,
                                                                                                   :type-specific-data 132,
                                                                                                   :type "varchar",
                                                                                                   :category "S",
                                                                                                   :primary-key? false,
                                                                                                   :enum? false},
                                                        :harja.domain.organisaatio/elynumero {:name "elynumero",
                                                                                              :number 11,
                                                                                              :not-null? false,
                                                                                              :has-default? false,
                                                                                              :type-specific-data -1,
                                                                                              :type "int2",
                                                                                              :category "N",
                                                                                              :primary-key? false,
                                                                                              :enum? false},
                                                        :harja.domain.organisaatio/muokattu {:name "muokattu",
                                                                                             :number 18,
                                                                                             :not-null? false,
                                                                                             :has-default? false,
                                                                                             :type-specific-data -1,
                                                                                             :type "timestamp",
                                                                                             :category "D",
                                                                                             :primary-key? false,
                                                                                             :enum? false},
                                                        :harja.domain.organisaatio/nimi {:name "nimi",
                                                                                         :number 3,
                                                                                         :not-null? true,
                                                                                         :has-default? false,
                                                                                         :type-specific-data 132,
                                                                                         :type "varchar",
                                                                                         :category "S",
                                                                                         :primary-key? false,
                                                                                         :enum? false},
                                                        :harja.domain.organisaatio/liikennemuoto {:name "liikennemuoto",
                                                                                                  :number 6,
                                                                                                  :not-null? false,
                                                                                                  :has-default? false,
                                                                                                  :type-specific-data -1,
                                                                                                  :type "liikennemuoto",
                                                                                                  :category "E",
                                                                                                  :primary-key? false,
                                                                                                  :enum? true},
                                                        :harja.domain.organisaatio/sampoid {:name "sampoid",
                                                                                            :number 10,
                                                                                            :not-null? false,
                                                                                            :has-default? false,
                                                                                            :type-specific-data 36,
                                                                                            :type "varchar",
                                                                                            :category "S",
                                                                                            :primary-key? false,
                                                                                            :enum? false},
                                                        :harja.domain.organisaatio/postinumero {:name "postinumero",
                                                                                                :number 9,
                                                                                                :not-null? false,
                                                                                                :has-default? false,
                                                                                                :type-specific-data 9,
                                                                                                :type "bpchar",
                                                                                                :category "S",
                                                                                                :primary-key? false,
                                                                                                :enum? false},
                                                        :harja.domain.organisaatio/lyhenne {:name "lyhenne",
                                                                                            :number 4,
                                                                                            :not-null? false,
                                                                                            :has-default? false,
                                                                                            :type-specific-data 20,
                                                                                            :type "varchar",
                                                                                            :category "S",
                                                                                            :primary-key? false,
                                                                                            :enum? false},
                                                        :harja.domain.organisaatio/katuosoite {:name "katuosoite",
                                                                                               :number 8,
                                                                                               :not-null? false,
                                                                                               :has-default? false,
                                                                                               :type-specific-data 132,
                                                                                               :type "varchar",
                                                                                               :category "S",
                                                                                               :primary-key? false,
                                                                                               :enum? false},
                                                        :harja.domain.organisaatio/harjassa-luotu? {:name "harjassa_luotu",
                                                                                                    :number 16,
                                                                                                    :not-null? true,
                                                                                                    :has-default? true,
                                                                                                    :type-specific-data -1,
                                                                                                    :type "bool",
                                                                                                    :category "B",
                                                                                                    :primary-key? false,
                                                                                                    :enum? false},
                                                        :harja.domain.organisaatio/tyyppi {:name "tyyppi",
                                                                                           :number 12,
                                                                                           :not-null? true,
                                                                                           :has-default? false,
                                                                                           :type-specific-data -1,
                                                                                           :type "organisaatiotyyppi",
                                                                                           :category "E",
                                                                                           :primary-key? false,
                                                                                           :enum? true},
                                                        :harja.domain.organisaatio/ulkoinen-id {:name "ulkoinen_id",
                                                                                                :number 15,
                                                                                                :not-null? false,
                                                                                                :has-default? false,
                                                                                                :type-specific-data -1,
                                                                                                :type "int4",
                                                                                                :category "N",
                                                                                                :primary-key? false,
                                                                                                :enum? false},
                                                        :harja.domain.organisaatio/ytunnus {:name "ytunnus",
                                                                                            :number 5,
                                                                                            :not-null? false,
                                                                                            :has-default? false,
                                                                                            :type-specific-data 13,
                                                                                            :type "bpchar",
                                                                                            :category "S",
                                                                                            :primary-key? false,
                                                                                            :enum? false},
                                                        :harja.domain.organisaatio/muokkaaja-id {:name "muokkaaja",
                                                                                                 :number 19,
                                                                                                 :not-null? false,
                                                                                                 :has-default? false,
                                                                                                 :type-specific-data -1,
                                                                                                 :type "int4",
                                                                                                 :category "N",
                                                                                                 :primary-key? false,
                                                                                                 :enum? false},
                                                        :harja.domain.organisaatio/alue {:name "alue",
                                                                                         :number 7,
                                                                                         :not-null? false,
                                                                                         :has-default? false,
                                                                                         :type-specific-data -1,
                                                                                         :type "geometry",
                                                                                         :category "U",
                                                                                         :primary-key? false,
                                                                                         :enum? false},
                                                        :harja.domain.organisaatio/poistettu {:name "poistettu",
                                                                                              :number 20,
                                                                                              :not-null? false,
                                                                                              :has-default? true,
                                                                                              :type-specific-data -1,
                                                                                              :type "bool",
                                                                                              :category "B",
                                                                                              :primary-key? false,
                                                                                              :enum? false},
                                                        :harja.domain.organisaatio/luotu {:name "luotu",
                                                                                          :number 17,
                                                                                          :not-null? false,
                                                                                          :has-default? false,
                                                                                          :type-specific-data -1,
                                                                                          :type "timestamp",
                                                                                          :category "D",
                                                                                          :primary-key? false,
                                                                                          :enum? false}},
                                              :insert-spec-kw :harja.domain.organisaatio/organisaatio-insert,
                                              :rel {"sampo_ely_hash" :harja.domain.organisaatio/sampo-ely-hash,
                                                    "harjassa_luotu" :harja.domain.organisaatio/harjassa-luotu?,
                                                    "ulkoinen_id" :harja.domain.organisaatio/ulkoinen-id,
                                                    "luoja" :harja.domain.organisaatio/luoja-id,
                                                    "muokkaaja" :harja.domain.organisaatio/muokkaaja-id}}})
  (do
    (clojure.spec/def
      :harja.domain.organisaatio/organisaatio
      (clojure.spec/keys
        :opt
        [:harja.domain.organisaatio/luoja-id
         :harja.domain.organisaatio/id
         :harja.domain.organisaatio/sampo-ely-hash
         :harja.domain.organisaatio/elynumero
         :harja.domain.organisaatio/muokattu
         :harja.domain.organisaatio/nimi
         :harja.domain.organisaatio/liikennemuoto
         :harja.domain.organisaatio/sampoid
         :harja.domain.organisaatio/postinumero
         :harja.domain.organisaatio/lyhenne
         :harja.domain.organisaatio/katuosoite
         :harja.domain.organisaatio/harjassa-luotu?
         :harja.domain.organisaatio/tyyppi
         :harja.domain.organisaatio/ulkoinen-id
         :harja.domain.organisaatio/ytunnus
         :harja.domain.organisaatio/muokkaaja-id
         :harja.domain.organisaatio/alue
         :harja.domain.organisaatio/poistettu
         :harja.domain.organisaatio/luotu]))
    (clojure.spec/def
      :harja.domain.organisaatio/organisaatio-insert
      (clojure.spec/keys
        :req
        [:harja.domain.organisaatio/nimi :harja.domain.organisaatio/tyyppi]
        :opt
        [:harja.domain.organisaatio/luoja-id
         :harja.domain.organisaatio/id
         :harja.domain.organisaatio/sampo-ely-hash
         :harja.domain.organisaatio/elynumero
         :harja.domain.organisaatio/muokattu
         :harja.domain.organisaatio/liikennemuoto
         :harja.domain.organisaatio/sampoid
         :harja.domain.organisaatio/postinumero
         :harja.domain.organisaatio/lyhenne
         :harja.domain.organisaatio/katuosoite
         :harja.domain.organisaatio/harjassa-luotu?
         :harja.domain.organisaatio/ulkoinen-id
         :harja.domain.organisaatio/ytunnus
         :harja.domain.organisaatio/muokkaaja-id
         :harja.domain.organisaatio/alue
         :harja.domain.organisaatio/poistettu
         :harja.domain.organisaatio/luotu]))
    (clojure.spec/def :harja.domain.organisaatio/luoja-id (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def :harja.domain.organisaatio/id :specql.data-types/int4)
    (clojure.spec/def
      :harja.domain.organisaatio/sampo-ely-hash
      (clojure.spec/nilable
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 128)))))
    (clojure.spec/def :harja.domain.organisaatio/elynumero (clojure.spec/nilable :specql.data-types/int2))
    (clojure.spec/def :harja.domain.organisaatio/muokattu (clojure.spec/nilable :specql.data-types/timestamp))
    (clojure.spec/def
      :harja.domain.organisaatio/nimi
      (clojure.spec/and
        :specql.data-types/varchar
        (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 128))))
    (clojure.spec/def :harja.domain.organisaatio/liikennemuoto (clojure.spec/nilable #{"T" "R" "V"}))
    (clojure.spec/def
      :harja.domain.organisaatio/sampoid
      (clojure.spec/nilable
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 32)))))
    (clojure.spec/def :harja.domain.organisaatio/postinumero (clojure.spec/nilable :specql.data-types/bpchar))
    (clojure.spec/def
      :harja.domain.organisaatio/lyhenne
      (clojure.spec/nilable
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 16)))))
    (clojure.spec/def
      :harja.domain.organisaatio/katuosoite
      (clojure.spec/nilable
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 128)))))
    (clojure.spec/def :harja.domain.organisaatio/harjassa-luotu? :specql.data-types/bool)
    (clojure.spec/def :harja.domain.organisaatio/tyyppi #{"hallintayksikko" "liikennevirasto" "urakoitsija"})
    (clojure.spec/def :harja.domain.organisaatio/ulkoinen-id (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def :harja.domain.organisaatio/ytunnus (clojure.spec/nilable :specql.data-types/bpchar))
    (clojure.spec/def :harja.domain.organisaatio/muokkaaja-id (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def :harja.domain.organisaatio/alue (clojure.spec/nilable :specql.data-types/geometry))
    (clojure.spec/def :harja.domain.organisaatio/poistettu (clojure.spec/nilable :specql.data-types/bool))
    (clojure.spec/def :harja.domain.organisaatio/luotu (clojure.spec/nilable :specql.data-types/timestamp))))

;; Haut

(s/def ::vesivayla-urakoitsijat-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::ytunnus ::katuosoite ::postinumero])))

;; Tallennus

(s/def ::tallenna-urakoitsija-kysely (s/keys :req [::nimi ::ytunnus]
                                             :opt [::id ::katuosoite ::postinumero]))

(s/def ::tallenna-urakoitsija-vastaus (s/keys :req [::id ::nimi ::ytunnus ::katuosoite ::postinumero]))