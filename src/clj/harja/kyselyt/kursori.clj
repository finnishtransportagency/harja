(ns harja.kyselyt.kursori
  "Apuri, jolla kursoria hyödyntäviä kyselyitä on helpompi kutsua"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.core.async :as async]))

(defn hae-kanavaan
  "Hakee kursorilla annetun kyselyn tulokset read-only transaktiossa.
  Käynnistää kyselyn suorituksen async säikeessä ja palauttaa kanavan."
  [ch-tai-koko db kysely-fn parametrit]
  (let [ch (if (number? ch-tai-koko)
             (async/chan ch-tai-koko)
             ch-tai-koko)]
    (async/thread
      (jdbc/with-db-transaction [db db
                                 {:read-only? true}]
        (kysely-fn db ch parametrit)))
    ch))
