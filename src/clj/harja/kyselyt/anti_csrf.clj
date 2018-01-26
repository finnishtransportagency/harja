(ns harja.kyselyt.anti-csrf
  "Hankkeisiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

(defqueries "harja/kyselyt/anti_csrf.sql"
  {:positional? true})

(def csrf-voimassa-s (* 60 60 24)) ;; 24h

(defn luo-csrf-sessio
  "Poistaa käyttäjän ei-voimassa olevat CSRF-sessiot ja luo uuden."
  [db kayttajanimi csrf-token]
  (poista-kayttajanimen-vanhentuneet-csrf-sessiot! db {:kayttajanimi kayttajanimi
                                                        :nyt (c/to-timestamp (t/now))})
  (luo-csrf-sessio-kayttajanimelle<!
    db
    {:kayttajanimi kayttajanimi
     :csrf_token csrf-token
     :nyt (c/to-timestamp (t/now))
     :voimassa (c/to-timestamp (t/plus (t/now)
                                       (t/seconds csrf-voimassa-s)))}))

(defn virkista-csrf-sessio-jos-voimassa [db kayttajanimi csrf-token]
  (virkista-kayttajanimen-csrf-sessio-jos-voimassa<!
    db
    {:kayttajanimi kayttajanimi
     :csrf_token csrf-token
     :nyt (c/to-timestamp (t/now))
     :voimassa (c/to-timestamp (t/plus (t/now)
                                       (t/seconds csrf-voimassa-s)))}))

(defn kayttajan-csrf-token-voimassa? [db kayttajanimi csrf-token]
  (some? (:id (first (hae-kayttajan-voimassaoleva-csrf-token
                       db
                       {:kayttajanimi kayttajanimi
                        :csrf_token csrf-token
                        :nyt (c/to-timestamp (t/now))})))))