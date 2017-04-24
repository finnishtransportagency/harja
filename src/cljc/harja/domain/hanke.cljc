(ns harja.domain.hanke
  "Määrittelee hankkeen specit"
  #?@(:clj [(:require [clojure.spec :as s]
                      [harja.kyselyt.specql-db :refer [db]]
                      [specql.core :refer [define-tables]]
                      [harja.domain.urakka :as u]
                      [clojure.future :refer :all])]
      :cljs [(:require [clojure.spec :as s]
               [specql.impl.registry]
               [harja.domain.urakka :as u]
               [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [db]]
               [specql.core :refer [define-tables]])]))

;; TODO Tämä on generoitu käyttäen macroa:
;; (macroexpand '(define-tables db ["hanke" ::hanke
;; {"harjassa_luotu" ::harjassa-luotu?
;;  "luoja" ::luoja-id
;;  "muokkaaja" ::muokkaaja-id}]))
;; Jouduttiin expandoimaan käsin, koska figwheel / phantom ei osannut käsitellä makroa sellaisenaan.
;; Vaatii pohtimista miten ratkaistaan.
(do
  (clojure.core/swap!
    specql.impl.registry/table-info-registry
    clojure.core/merge
    {:harja.domain.hanke/hanke {:name "hanke",
                                :type :table,
                                :columns {:harja.domain.hanke/loppupvm {:name "loppupvm",
                                                                        :number 4,
                                                                        :not-null? true,
                                                                        :has-default? false,
                                                                        :type-specific-data -1,
                                                                        :type "date",
                                                                        :category "D",
                                                                        :primary-key? false,
                                                                        :enum? false},
                                          :harja.domain.hanke/luotu {:name "luotu",
                                                                     :number 9,
                                                                     :not-null? false,
                                                                     :has-default? false,
                                                                     :type-specific-data -1,
                                                                     :type "timestamp",
                                                                     :category "D",
                                                                     :primary-key? false,
                                                                     :enum? false},
                                          :harja.domain.hanke/harjassa-luotu? {:name "harjassa_luotu",
                                                                               :number 8,
                                                                               :not-null? true,
                                                                               :has-default? true,
                                                                               :type-specific-data -1,
                                                                               :type "bool",
                                                                               :category "B",
                                                                               :primary-key? false,
                                                                               :enum? false},
                                          :harja.domain.hanke/muokkaaja-id {:name "muokkaaja",
                                                                            :number 12,
                                                                            :not-null? false,
                                                                            :has-default? false,
                                                                            :type-specific-data -1,
                                                                            :type "int4",
                                                                            :category "N",
                                                                            :primary-key? false,
                                                                            :enum? false},
                                          :harja.domain.hanke/id {:name "id",
                                                                  :number 1,
                                                                  :not-null? true,
                                                                  :has-default? true,
                                                                  :type-specific-data -1,
                                                                  :type "int4",
                                                                  :category "N",
                                                                  :primary-key? true,
                                                                  :enum? false},
                                          :harja.domain.hanke/alkupvm {:name "alkupvm",
                                                                       :number 3,
                                                                       :not-null? true,
                                                                       :has-default? false,
                                                                       :type-specific-data -1,
                                                                       :type "date",
                                                                       :category "D",
                                                                       :primary-key? false,
                                                                       :enum? false},
                                          :harja.domain.hanke/muokattu {:name "muokattu",
                                                                        :number 10,
                                                                        :not-null? false,
                                                                        :has-default? false,
                                                                        :type-specific-data -1,
                                                                        :type "timestamp",
                                                                        :category "D",
                                                                        :primary-key? false,
                                                                        :enum? false},
                                          :harja.domain.hanke/poistettu {:name "poistettu",
                                                                         :number 13,
                                                                         :not-null? false,
                                                                         :has-default? true,
                                                                         :type-specific-data -1,
                                                                         :type "bool",
                                                                         :category "B",
                                                                         :primary-key? false,
                                                                         :enum? false},
                                          :harja.domain.hanke/nimi {:name "nimi",
                                                                    :number 2,
                                                                    :not-null? true,
                                                                    :has-default? false,
                                                                    :type-specific-data 132,
                                                                    :type "varchar",
                                                                    :category "S",
                                                                    :primary-key? false,
                                                                    :enum? false},
                                          :harja.domain.hanke/sampoid {:name "sampoid",
                                                                       :number 6,
                                                                       :not-null? false,
                                                                       :has-default? false,
                                                                       :type-specific-data 36,
                                                                       :type "varchar",
                                                                       :category "S",
                                                                       :primary-key? false,
                                                                       :enum? false},
                                          :harja.domain.hanke/luoja-id {:name "luoja",
                                                                        :number 11,
                                                                        :not-null? false,
                                                                        :has-default? false,
                                                                        :type-specific-data -1,
                                                                        :type "int4",
                                                                        :category "N",
                                                                        :primary-key? false,
                                                                        :enum? false}},
                                :insert-spec-kw :harja.domain.hanke/hanke-insert,
                                :rel {"harjassa_luotu" :harja.domain.hanke/harjassa-luotu?,
                                      "luoja" :harja.domain.hanke/luoja-id,
                                      "muokkaaja" :harja.domain.hanke/muokkaaja-id}}})
  (do
    (clojure.spec/def
      :harja.domain.hanke/hanke
      (clojure.spec/keys
        :opt
        [:harja.domain.hanke/loppupvm
         :harja.domain.hanke/luotu
         :harja.domain.hanke/harjassa-luotu?
         :harja.domain.hanke/muokkaaja-id
         :harja.domain.hanke/id
         :harja.domain.hanke/alkupvm
         :harja.domain.hanke/muokattu
         :harja.domain.hanke/poistettu
         :harja.domain.hanke/nimi
         :harja.domain.hanke/sampoid
         :harja.domain.hanke/luoja-id]))
    (clojure.spec/def
      :harja.domain.hanke/hanke-insert
      (clojure.spec/keys
        :req
        [:harja.domain.hanke/loppupvm :harja.domain.hanke/alkupvm :harja.domain.hanke/nimi]
        :opt
        [:harja.domain.hanke/luotu
         :harja.domain.hanke/harjassa-luotu?
         :harja.domain.hanke/muokkaaja-id
         :harja.domain.hanke/id
         :harja.domain.hanke/muokattu
         :harja.domain.hanke/poistettu
         :harja.domain.hanke/sampoid
         :harja.domain.hanke/luoja-id]))
    (clojure.spec/def :harja.domain.hanke/loppupvm :specql.data-types/date)
    (clojure.spec/def :harja.domain.hanke/luotu (clojure.spec/nilable :specql.data-types/timestamp))
    (clojure.spec/def :harja.domain.hanke/harjassa-luotu? :specql.data-types/bool)
    (clojure.spec/def :harja.domain.hanke/muokkaaja-id (clojure.spec/nilable :specql.data-types/int4))
    (clojure.spec/def :harja.domain.hanke/id :specql.data-types/int4)
    (clojure.spec/def :harja.domain.hanke/alkupvm :specql.data-types/date)
    (clojure.spec/def :harja.domain.hanke/muokattu (clojure.spec/nilable :specql.data-types/timestamp))
    (clojure.spec/def :harja.domain.hanke/poistettu (clojure.spec/nilable :specql.data-types/bool))
    (clojure.spec/def
      :harja.domain.hanke/nimi
      (clojure.spec/and
        :specql.data-types/varchar
        (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 128))))
    (clojure.spec/def
      :harja.domain.hanke/sampoid
      (clojure.spec/nilable
        (clojure.spec/and
          :specql.data-types/varchar
          (clojure.core/fn [s__23480__auto__] (clojure.core/<= (clojure.core/count s__23480__auto__) 32)))))
    (clojure.spec/def :harja.domain.hanke/luoja-id (clojure.spec/nilable :specql.data-types/int4))))

;; Haut

(s/def ::hae-harjassa-luodut-hankkeet-vastaus
  (s/coll-of (s/keys :req [::id ::alkupvm ::loppupvm ::nimi]
                     :opt [::u/urakka])))

;; Tallennus

(s/def ::tallenna-hanke-kysely (s/keys :req [::alkupvm ::loppupvm ::nimi]
                                       :opt [::id]))

(s/def ::tallenna-hanke-vastaus (s/keys :req [::id ::alkupvm ::loppupvm ::nimi]))