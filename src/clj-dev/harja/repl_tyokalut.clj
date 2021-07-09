(ns harja.repl-tyokalut
  (:require [harja.palvelin.main :as main]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]))

(defn dev-start []
  (if main/harja-jarjestelma
    (log/info "Harja on jo käynnissä!")
    (main/kaynnista-jarjestelma main/asetukset-tiedosto false)))

(defn dev-stop []
  (main/sammuta-jarjestelma))

(defn dev-restart []
  (dev-stop)
  (dev-start)
  :ok)


(defn dev-julkaise
  "REPL käyttöön: julkaise uusi palvelu (poistaa ensin vanhan samalla nimellä)."
  [nimi fn]
  (http-palvelin/poista-palvelu (:http-palvelin main/harja-jarjestelma) nimi)
  (http-palvelin/julkaise-palvelu (:http-palvelin main/harja-jarjestelma) nimi fn))

(defmacro with-db [s & body]
  `(let [~s (:db main/harja-jarjestelma)]
     ~@body))

(defn q
  "Kysele Harjan kannasta, REPL kehitystä varten"
  [& sql]
  (with-open [c (.getConnection (:datasource (:db main/harja-jarjestelma)))
              ps (.prepareStatement c (reduce str sql))
              rs (.executeQuery ps)]
    (let [cols (-> (.getMetaData rs) .getColumnCount)]
      (loop [res []
             more? (.next rs)]
        (if-not more?
          res
          (recur (conj res (loop [row []
                                  i 1]
                             (if (<= i cols)
                               (recur (conj row (.getObject rs i)) (inc i))
                               row)))
                 (.next rs)))))))

(defn u
  "UPDATE Harjan kantaan"
  [& sql]
  (with-open [c (.getConnection (:datasource (:db main/harja-jarjestelma)))
              ps (.prepareStatement c (reduce str sql))]
    (.executeUpdate ps)))

(defn explain [sql]
  (q "EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) " sql))