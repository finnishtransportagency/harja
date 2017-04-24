(ns harja.domain.sopimus
  "Määrittelee sopimuksen specit"
  #?@(:clj [(:require [clojure.spec :as s]
                      [harja.id :refer [id-olemassa?]]
                      [harja.kyselyt.specql-db :refer [db]]
                      [specql.core :refer [define-tables]]
                      [clojure.future :refer :all])]
      :cljs [(:require [clojure.spec :as s]
               [harja.id :refer [id-olemassa?]]
               [specql.impl.registry]
               [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [db]]
               [specql.core :refer [define-tables]])]))

;; TODO Tämä on generoitu käyttäen macroa:
;; (macroexpand '(define-tables db ["sopimus" ::sopimus
;; {"paasopimus" ::paasopimus-id
;;  "harjassa_luotu" ::harjassa-luotu?
;;  "urakoitsija_sampoid" ::urakoitsija-sampoid
;;  "urakka_sampoid" ::urakka-sampoid
;;  "luoja" ::luoja-id
;;  "urakka" ::urakka-id
;;  "muokkaaja" ::muokkaaja-id}]))
;; Jouduttiin expandoimaan käsin, koska figwheel / phantom ei osannut käsitellä makroa sellaisenaan.
;; Vaatii pohtimista miten ratkaistaan.
(do
  (clojure.core/swap!
    specql.impl.registry/table-info-registry
    clojure.core/merge
    {:harja.domain.sopimus/sopimus {:name "sopimus",
                                    :type :table,
                                    :columns {:harja.domain.sopimus/paasopimus {:name "paasopimus",
                                                                                :number 7,
                                                                                :not-null? false,
                                                                                :has-default? false,
                                                                                :type-specific-data -1,
                                                                                :type "int4",
                                                                                :category "N",
                                                                                :primary-key? false,
                                                                                :enum? false},
                                              :harja.domain.sopimus/luoja {:name "luoja",
                                                                           :number 13,
                                                                           :not-null? false,
                                                                           :has-default? false,
                                                                           :type-specific-data -1,
                                                                           :type "int4",
                                                                           :category "N",
                                                                           :primary-key? false,
                                                                           :enum? false},
                                              :harja.domain.sopimus/id {:name "id",
                                                                        :number 1,
                                                                        :not-null? true,
                                                                        :has-default? true,
                                                                        :type-specific-data -1,
                                                                        :type "int4",
                                                                        :category "N",
                                                                        :primary-key? true,
                                                                        :enum? false},
                                              :harja.domain.sopimus/loppupvm {:name "loppupvm",
                                                                              :number 4,
                                                                              :not-null? true,
                                                                              :has-default? false,
                                                                              :type-specific-data -1,
                                                                              :type "date",
                                                                              :category "D",
                                                                              :primary-key? false,
                                                                              :enum? false},
                                              :harja.domain.sopimus/nimi {:name "nimi",
                                                                          :number 2,
                                                                          :not-null? true,
                                                                          :has-default? false,
                                                                          :type-specific-data 132,
                                                                          :type "varchar",
                                                                          :category "S",
                                                                          :primary-key? false,
                                                                          :enum? false},
                                              :harja.domain.sopimus/harjassa_luotu {:name "harjassa_luotu",
                                                                                    :number 10,
                                                                                    :not-null? true,
                                                                                    :has-default? true,
                                                                                    :type-specific-data -1,
                                                                                    :type "bool",
                                                                                    :category "B",
                                                                                    :primary-key? false,
                                                                                    :enum? false},
                                              :harja.domain.sopimus/muokattu {:name "muokattu",
                                                                              :number 12,
                                                                              :not-null? false,
                                                                              :has-default? false,
                                                                              :type-specific-data -1,
                                                                              :type "timestamp",
                                                                              :category "D",
                                                                              :primary-key? false,
                                                                              :enum? false},
                                              :harja.domain.sopimus/sampoid {:name "sampoid",
                                                                             :number 5,
                                                                             :not-null? false,
                                                                             :has-default? false,
                                                                             :type-specific-data 36,
                                                                             :type "varchar",
                                                                             :category "S",
                                                                             :primary-key? false,
                                                                             :enum? false},
                                              :harja.domain.sopimus/urakoitsija_sampoid {:name "urakoitsija_sampoid",
                                                                                         :number 9,
                                                                                         :not-null? false,
                                                                                         :has-default? false,
                                                                                         :type-specific-data 20,
                                                                                         :type "varchar",
                                                                                         :category "S",
                                                                                         :primary-key? false,
                                                                                         :enum? false},
                                              :harja.domain.sopimus/urakka_sampoid {:name "urakka_sampoid",
                                                                                    :number 8,
                                                                                    :not-null? false,
                                                                                    :has-default? false,
                                                                                    :type-specific-data 20,
                                                                                    :type "varchar",
                                                                                    :category "S",
                                                                                    :primary-key? false,
                                                                                    :enum? false},
                                              :harja.domain.sopimus/alkupvm {:name "alkupvm",
                                                                             :number 3,
                                                                             :not-null? true,
                                                                             :has-default? false,
                                                                             :type-specific-data -1,
                                                                             :type "date",
                                                                             :category "D",
                                                                             :primary-key? false,
                                                                             :enum? false},
                                              :harja.domain.sopimus/urakka {:name "urakka",
                                                                            :number 6,
                                                                            :not-null? false,
                                                                            :has-default? false,
                                                                            :type-specific-data -1,
                                                                            :type "int4",
                                                                            :category "N",
                                                                            :primary-key? false,
                                                                            :enum? false},
                                              :harja.domain.sopimus/muokkaaja {:name "muokkaaja",
                                                                               :number 14,
                                                                               :not-null? false,
                                                                               :has-default? false,
                                                                               :type-specific-data -1,
                                                                               :type "int4",
                                                                               :category "N",
                                                                               :primary-key? false,
                                                                               :enum? false},
                                              :harja.domain.sopimus/poistettu {:name "poistettu",
                                                                               :number 15,
                                                                               :not-null? false,
                                                                               :has-default? true,
                                                                               :type-specific-data -1,
                                                                               :type "bool",
                                                                               :category "B",
                                                                               :primary-key? false,
                                                                               :enum? false},
                                              :harja.domain.sopimus/luotu {:name "luotu",
                                                                           :number 11,
                                                                           :not-null? false,
                                                                           :has-default? false,
                                                                           :type-specific-data -1,
                                                                           :type "timestamp",
                                                                           :category "D",
                                                                           :primary-key? false,
                                                                           :enum? false}},
                                    :insert-spec-kw :harja.domain.sopimus/sopimus-insert,
                                    :rel nil}})
  (do
    (clojure.core/swap!
      specql.impl.registry/table-info-registry
      clojure.core/merge
      {:harja.domain.sopimus/sopimus {:name "sopimus",
                                      :type :table,
                                      :columns {:harja.domain.sopimus/urakka-sampoid {:name "urakka_sampoid",
                                                                                      :number 8,
                                                                                      :not-null? false,
                                                                                      :has-default? false,
                                                                                      :type-specific-data 20,
                                                                                      :type "varchar",
                                                                                      :category "S",
                                                                                      :primary-key? false,
                                                                                      :enum? false},
                                                :harja.domain.sopimus/luoja-id {:name "luoja",
                                                                                :number 13,
                                                                                :not-null? false,
                                                                                :has-default? false,
                                                                                :type-specific-data -1,
                                                                                :type "int4",
                                                                                :category "N",
                                                                                :primary-key? false,
                                                                                :enum? false},
                                                :harja.domain.sopimus/paasopimus-id {:name "paasopimus",
                                                                                     :number 7,
                                                                                     :not-null? false,
                                                                                     :has-default? false,
                                                                                     :type-specific-data -1,
                                                                                     :type "int4",
                                                                                     :category "N",
                                                                                     :primary-key? false,
                                                                                     :enum? false},
                                                :harja.domain.sopimus/id {:name "id",
                                                                          :number 1,
                                                                          :not-null? true,
                                                                          :has-default? true,
                                                                          :type-specific-data -1,
                                                                          :type "int4",
                                                                          :category "N",
                                                                          :primary-key? true,
                                                                          :enum? false},
                                                :harja.domain.sopimus/loppupvm {:name "loppupvm",
                                                                                :number 4,
                                                                                :not-null? true,
                                                                                :has-default? false,
                                                                                :type-specific-data -1,
                                                                                :type "date",
                                                                                :category "D",
                                                                                :primary-key? false,
                                                                                :enum? false},
                                                :harja.domain.sopimus/nimi {:name "nimi",
                                                                            :number 2,
                                                                            :not-null? true,
                                                                            :has-default? false,
                                                                            :type-specific-data 132,
                                                                            :type "varchar",
                                                                            :category "S",
                                                                            :primary-key? false,
                                                                            :enum? false},
                                                :harja.domain.sopimus/muokattu {:name "muokattu",
                                                                                :number 12,
                                                                                :not-null? false,
                                                                                :has-default? false,
                                                                                :type-specific-data -1,
                                                                                :type "timestamp",
                                                                                :category "D",
                                                                                :primary-key? false,
                                                                                :enum? false},
                                                :harja.domain.sopimus/urakoitsija-sampoid {:name "urakoitsija_sampoid",
                                                                                           :number 9,
                                                                                           :not-null? false,
                                                                                           :has-default? false,
                                                                                           :type-specific-data 20,
                                                                                           :type "varchar",
                                                                                           :category "S",
                                                                                           :primary-key? false,
                                                                                           :enum? false},
                                                :harja.domain.sopimus/harjassa-luotu? {:name "harjassa_luotu",
                                                                                       :number 10,
                                                                                       :not-null? true,
                                                                                       :has-default? true,
                                                                                       :type-specific-data -1,
                                                                                       :type "bool",
                                                                                       :category "B",
                                                                                       :primary-key? false,
                                                                                       :enum? false},
                                                :harja.domain.sopimus/sampoid {:name "sampoid",
                                                                               :number 5,
                                                                               :not-null? false,
                                                                               :has-default? false,
                                                                               :type-specific-data 36,
                                                                               :type "varchar",
                                                                               :category "S",
                                                                               :primary-key? false,
                                                                               :enum? false},
                                                :harja.domain.sopimus/muokkaaja-id {:name "muokkaaja",
                                                                                    :number 14,
                                                                                    :not-null? false,
                                                                                    :has-default? false,
                                                                                    :type-specific-data -1,
                                                                                    :type "int4",
                                                                                    :category "N",
                                                                                    :primary-key? false,
                                                                                    :enum? false},
                                                :harja.domain.sopimus/alkupvm {:name "alkupvm",
                                                                               :number 3,
                                                                               :not-null? true,
                                                                               :has-default? false,
                                                                               :type-specific-data -1,
                                                                               :type "date",
                                                                               :category "D",
                                                                               :primary-key? false,
                                                                               :enum? false},
                                                :harja.domain.sopimus/urakka-id {:name "urakka",
                                                                              :number 6,
                                                                              :not-null? false,
                                                                              :has-default? false,
                                                                              :type-specific-data -1,
                                                                              :type "int4",
                                                                              :category "N",
                                                                              :primary-key? false,
                                                                              :enum? false},
                                                :harja.domain.sopimus/poistettu {:name "poistettu",
                                                                                 :number 15,
                                                                                 :not-null? false,
                                                                                 :has-default? true,
                                                                                 :type-specific-data -1,
                                                                                 :type "bool",
                                                                                 :category "B",
                                                                                 :primary-key? false,
                                                                                 :enum? false},
                                                :harja.domain.sopimus/luotu {:name "luotu",
                                                                             :number 11,
                                                                             :not-null? false,
                                                                             :has-default? false,
                                                                             :type-specific-data -1,
                                                                             :type "timestamp",
                                                                             :category "D",
                                                                             :primary-key? false,
                                                                             :enum? false}},
                                      :insert-spec-kw :harja.domain.sopimus/sopimus-insert,
                                      :rel {"paasopimus" :harja.domain.sopimus/paasopimus-id,
                                            "harjassa_luotu" :harja.domain.sopimus/harjassa-luotu?,
                                            "urakoitsija_sampoid" :harja.domain.sopimus/urakoitsija-sampoid,
                                            "urakka_sampoid" :harja.domain.sopimus/urakka-sampoid,
                                            "luoja" :harja.domain.sopimus/luoja-id,
                                            "muokkaaja" :harja.domain.sopimus/muokkaaja-id}}})
    (do
      (clojure.spec/def
        :harja.domain.sopimus/sopimus
        (clojure.spec/keys
          :opt
          [:harja.domain.sopimus/urakka-sampoid
           :harja.domain.sopimus/luoja-id
           :harja.domain.sopimus/paasopimus-id
           :harja.domain.sopimus/id
           :harja.domain.sopimus/loppupvm
           :harja.domain.sopimus/nimi
           :harja.domain.sopimus/muokattu
           :harja.domain.sopimus/urakoitsija-sampoid
           :harja.domain.sopimus/harjassa-luotu?
           :harja.domain.sopimus/sampoid
           :harja.domain.sopimus/muokkaaja-id
           :harja.domain.sopimus/alkupvm
           :harja.domain.sopimus/urakka-id
           :harja.domain.sopimus/poistettu
           :harja.domain.sopimus/luotu]))
      (clojure.spec/def
        :harja.domain.sopimus/sopimus-insert
        (clojure.spec/keys
          :req
          [:harja.domain.sopimus/loppupvm :harja.domain.sopimus/nimi :harja.domain.sopimus/alkupvm]
          :opt
          [:harja.domain.sopimus/urakka-sampoid
           :harja.domain.sopimus/luoja-id
           :harja.domain.sopimus/paasopimus-id
           :harja.domain.sopimus/id
           :harja.domain.sopimus/muokattu
           :harja.domain.sopimus/urakoitsija-sampoid
           :harja.domain.sopimus/harjassa-luotu?
           :harja.domain.sopimus/sampoid
           :harja.domain.sopimus/muokkaaja-id
           :harja.domain.sopimus/urakka-id
           :harja.domain.sopimus/poistettu
           :harja.domain.sopimus/luotu]))
      (clojure.spec/def
        :harja.domain.sopimus/urakka-sampoid
        (clojure.spec/nilable
          (clojure.spec/and
            :specql.data-types/varchar
            (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 16)))))
      (clojure.spec/def :harja.domain.sopimus/luoja-id (clojure.spec/nilable :specql.data-types/int4))
      (clojure.spec/def :harja.domain.sopimus/paasopimus-id (clojure.spec/nilable :specql.data-types/int4))
      (clojure.spec/def :harja.domain.sopimus/id :specql.data-types/int4)
      (clojure.spec/def :harja.domain.sopimus/loppupvm :specql.data-types/date)
      (clojure.spec/def
        :harja.domain.sopimus/nimi
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 128))))
      (clojure.spec/def :harja.domain.sopimus/muokattu (clojure.spec/nilable :specql.data-types/timestamp))
      (clojure.spec/def
        :harja.domain.sopimus/urakoitsija-sampoid
        (clojure.spec/nilable
          (clojure.spec/and
            :specql.data-types/varchar
            (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 16)))))
      (clojure.spec/def :harja.domain.sopimus/harjassa-luotu? :specql.data-types/bool)
      (clojure.spec/def
        :harja.domain.sopimus/sampoid
        (clojure.spec/nilable
          (clojure.spec/and
            :specql.data-types/varchar
            (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 32)))))
      (clojure.spec/def :harja.domain.sopimus/muokkaaja-id (clojure.spec/nilable :specql.data-types/int4))
      (clojure.spec/def :harja.domain.sopimus/alkupvm :specql.data-types/date)
      (clojure.spec/def :harja.domain.sopimus/urakka-id (clojure.spec/nilable :specql.data-types/int4))
      (clojure.spec/def :harja.domain.sopimus/poistettu (clojure.spec/nilable :specql.data-types/bool))
      (clojure.spec/def :harja.domain.sopimus/luotu (clojure.spec/nilable :specql.data-types/timestamp)))))

;; Haut

(s/def ::hae-harjassa-luodut-sopimukset-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::alkupvm ::loppupvm ::paasopimus-id])))

;; Tallennukset

(s/def ::tallenna-sopimus-kysely (s/keys
                                   :req [::nimi ::alkupvm ::loppupvm]
                                   :opt [::id ::paasopimus-id]))

(s/def ::tallenna-sopimus-vastaus (s/keys :req [::id ::nimi ::alkupvm ::loppupvm ::paasopimus-id]))


(defn paasopimus [sopimukset]
  (let [ps (as-> sopimukset s
                 (filter (comp id-olemassa? ::s/id) s)
                 (remove :poistettu s)
                 (filter #(nil? (::s/paasopimus-id %)) s))]
    (assert (>= 1 (count ps)) (str (pr-str sopimukset) " löytyi useampi kuin yksi pääsopimus"))
    (first ps)))