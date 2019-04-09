(ns harja.kyselyt.anti-csrf
  "Hankkeisiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

(defqueries "harja/kyselyt/anti_csrf.sql"
  {:positional? true})

(def csrf-voimassa-s (* 60 60 24)) ;; 24h

(defn poista-ja-luo-csrf-sessio
  "Poistaa käyttäjän ei-voimassa olevat CSRF-sessiot ja luo uuden."
  [db kayttajanimi csrf-token nyt]
  (poista-kayttajanimen-vanhentuneet-csrf-sessiot! db {:kayttajanimi kayttajanimi
                                                        :nyt (c/to-timestamp nyt)})
  (luo-csrf-sessio-kayttajanimelle<!
    db
    {:kayttajanimi kayttajanimi
     :csrf_token csrf-token
     :nyt (c/to-timestamp nyt)
     :voimassa (c/to-timestamp (t/plus nyt
                                       (t/seconds csrf-voimassa-s)))}))

(defn virkista-csrf-sessio-jos-voimassa [db kayttajanimi csrf-token nyt]
  (virkista-kayttajanimen-csrf-sessio-jos-voimassa<!
    db
    {:kayttajanimi kayttajanimi
     :csrf_token csrf-token
     :nyt (c/to-timestamp nyt)
     :voimassa (c/to-timestamp (t/plus nyt (t/seconds csrf-voimassa-s)))}))

(defn kayttajan-csrf-sessio-voimassa? [db kayttajanimi csrf-token nyt]
  (some? (:id (first (hae-kayttajan-voimassaoleva-csrf-token
                       db
                       {:kayttajanimi kayttajanimi
                        :csrf_token csrf-token
                        :nyt (c/to-timestamp nyt)})))))
