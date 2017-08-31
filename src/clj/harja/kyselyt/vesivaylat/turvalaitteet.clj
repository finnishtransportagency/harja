(ns harja.kyselyt.vesivaylat.turvalaitteet
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [namespacefy.core :as namespacefy]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.op :as op]
            [specql.rel :as rel]

            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [specql.transform :as xf]
            [clojure.string :as str]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/vesivaylat/turvalaitteet.sql")

(defrecord &&op [values]
  op/Op
  (to-sql [_ v {transform ::xf/transform type :type enum? :enum? :as column-info}]
    (let [values (if transform
                   (map #(xf/to-sql transform %) values)
                   values)
          param (if enum?
                  (str "?::" type)
                  "?")]
      [(str v " && ARRAY["
            (str/join "," (repeat (count values) param))
            "]" (when type (str "::" type)))
       (vec values)])))

(defn && [values]
  (assert (clojure.core/and
            (clojure.core/or (nil? values)
                             (coll? values))
            (clojure.core/not (map? values)))
          "&& op requires a collection of values")
  (->&&op values))

(defn hae-turvalaitteet-kartalle [db {:keys [turvalaitenumerot vaylanumerot]}]
  (when (or (not-empty turvalaitenumerot)
            (not-empty vaylanumerot))
    (into []
          (comp
            (geo/muunna-pg-tulokset ::tu/sijainti))
          (specql/fetch db
                        ::tu/turvalaite
                        #{::tu/id
                          ::tu/nimi
                          ::tu/sijainti
                          ::tu/turvalaitenro
                          ::tu/tyyppi
                          ::tu/kiintea
                          ::tu/vaylat}
                        (op/and
                          {::m/poistettu? false}
                          (when (not-empty turvalaitenumerot)
                            {::tu/turvalaitenro (op/in turvalaitenumerot)})
                          (when (not-empty vaylanumerot)
                            {::tu/vaylat (&& (vec vaylanumerot))}))))))

(defn hae-turvalaitteet-tekstilla [db {:keys [hakuteksti]}]
  (let [hakuteksti-numerona (try
                              (Integer. hakuteksti)
                              (catch Exception e))]
    (vec (specql/fetch db ::tu/turvalaite
                       #{::tu/id
                         ::tu/nimi
                         ::tu/turvalaitenro}
                       (op/or (merge
                                {::tu/nimi (op/ilike (str hakuteksti "%"))}
                                (when hakuteksti-numerona
                                  {::tu/turvalaitenro hakuteksti})))))))

