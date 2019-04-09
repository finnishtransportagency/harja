(ns harja.kyselyt.vesivaylat.alukset
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            
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

(defqueries "harja/kyselyt/vesivaylat/alukset.sql")

(defn oletus-alku-reitin-hakemiselle [] (t/minus (t/now) (t/days 2)))
(defn oletus-loppu-reitin-hakemiselle [] (t/now))

(def alus-xf
  (comp
    (map #(update % :sijainti geo/pg->clj))))

(def
  ^{:doc "Aluksen reitein muodostamat pisteet nousevat kannasta pg-arrayna, joka sisältää
  pgobjecteja, joissa ensimmäinen elementti on aikaleima, ja toinen PGGeometria (tai PGPoint..).
  Koska pgobject sisältää todellisuudessa merkkijonoja, täytyy elementtien sisältö parsia.
  Tässä transducerissa :pisteet muutetaan vektoriksi, ja pgbobjectin sisältö parsitaan aikaleimaksi
  ja clj-pointiksi, ja vektorin sisältö muutetaan mäpeiksi joissa avaimet :aika ja :sijainti.
  Lopulta :aika ja :sijainti laitetaan namespaceen ::alus, eli ::alus/aika ja ::alus/sijainti"}
  pisteet-xf
  (comp
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
                                         (let [[alku loppu] (-> s ;; "(123.0,456.5)"
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
                                 pisteet))))))

(defn- alusten-reitit* [alukset]
  (->
    (into []
          alus-xf
          alukset)
    (namespacefy {:ns :harja.domain.vesivaylat.alus})))

(defn alusten-reitit [db {:keys [alukset alku loppu]}]
  (let [loppu (or loppu (oletus-loppu-reitin-hakemiselle))
        alku (or alku (oletus-alku-reitin-hakemiselle))]
    (alusten-reitit*
      (hae-alusten-reitit db {:alukset (vec alukset)
                             :alku (coerce/to-sql-time alku)
                             :loppu (coerce/to-sql-time loppu)}))))

(defn- alusten-reitit-pisteineen* [alukset]
  (->
    (into []
          (comp
            alus-xf
            pisteet-xf)
          alukset)
    (namespacefy {:ns :harja.domain.vesivaylat.alus})))

(defn alusten-reitit-pisteineen [db {:keys [alukset alku loppu]}]
  (let [loppu (or loppu (oletus-loppu-reitin-hakemiselle))
        alku (or alku (oletus-alku-reitin-hakemiselle))]
    (alusten-reitit-pisteineen*
      (hae-alusten-reitit-pisteineen db {:alukset (vec alukset)
                                        :alku (coerce/to-sql-time alku)
                                        :loppu (coerce/to-sql-time loppu)}))))
