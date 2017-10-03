(ns harja.kyselyt.vesivaylat.alukset
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.id :refer [id-olemassa?]]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [harja.geo :as geo]
            [specql.core :as specql]
            [harja.domain.vesivaylat.alus :as alus]
            [specql.op :as op]
            [namespacefy.core :refer [namespacefy]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]))

(defqueries "harja/kyselyt/vesivaylat/alukset.sql"
  {:positional? true})

(defn oletus-alku-reitin-hakemiselle [] (t/minus (t/now) (t/days 2)))
(defn oletus-loppu-reitin-hakemiselle [] (t/now))

(defn alusten-reitit [db {:keys [alukset alku loppu]}]
  (let [loppu (or loppu (oletus-loppu-reitin-hakemiselle))
        alku (or alku (oletus-alku-reitin-hakemiselle))]
    (->
      (into []
           (comp
             (map #(update % :sijainti geo/pg->clj))
             (map #(konv/array->vec % :pisteet))
             (map #(update % :pisteet (fn [pisteet]
                                        (map
                                          (comp
                                            (fn [m]
                                              (namespacefy m {:ns :harja.domain.vesivaylat.alus}))
                                            (fn [aika-piste]
                                              (update
                                                aika-piste
                                                :sijainti
                                                (fn [s]
                                                  (let [[alku loppu] (-> s
                                                                         rest
                                                                         butlast
                                                                         ((partial apply str))
                                                                       (str/split #","))]
                                                    {:type :point
                                                     :coordinates [(Float/parseFloat alku)
                                                                   (Float/parseFloat loppu)]}))))
                                            (fn [o]
                                             (konv/pgobject->map o
                                                                 :aika :date
                                                                 :sijainti :string)))
                                          pisteet)))))
           (hae-alusten-reitit db))
      (namespacefy {:ns :harja.domain.vesivaylat.alus}))))