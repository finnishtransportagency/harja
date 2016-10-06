(ns harja.kyselyt.yhteystarkistukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/yhteystarkistukset.sql"
            {:positional? true})

(defn hae-viimeisin-ajoaika [db nimi]
  (:viimeisin_tarkastus
    (first (harja.kyselyt.yhteystarkistukset/hae-yhteystarkistus db nimi))))
