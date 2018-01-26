(ns harja.kyselyt.anti-csrf
  "Hankkeisiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

(defqueries "harja/kyselyt/anti_csrf.sql"
  {:positional? true})

(def csrf-voimassa-s (* 60 60 24)) ;; 24h

(defn luo-csrf-sessio [db kayttajanimi csrf-token]
  (luo-csrf-sessio-kayttajanimelle<!
    db
    {:kayttajanimi kayttajanimi
     :csrf_token csrf-token
     :voimassa (c/to-timestamp (t/plus (t/now)
                                       (t/seconds csrf-voimassa-s)))}))

(defn virkista-csrf-sessio-jos-voimassa [db kayttajanimi csrf-token]
  (virkista-kayttajanimen-csrf-sessio-jos-voimassa<!
    db
    {:kayttajanimi kayttajanimi
     :csrf_token csrf-token
     :voimassa (c/to-timestamp (t/plus (t/now)
                                       (t/seconds csrf-voimassa-s)))}))