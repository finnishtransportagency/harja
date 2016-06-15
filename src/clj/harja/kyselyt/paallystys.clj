(ns harja.kyselyt.paallystys
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/paallystys.sql"
  {:positional? true})

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn hae-urakan-paallystysilmoitukset-kohteineen[db urakka-id sopimus-id]
  (into []
        (comp
          (map #(konv/string-poluista->keyword % [[:paatos-taloudellinen-osa]
                                                  [:paatos-tekninen-osa]
                                                  [:tila]]))
          (map #(assoc % :kohdeosat
                         (into []
                               kohdeosa-xf
                               (yllapitokohteet-q/hae-urakan-yllapitokohteen-yllapitokohdeosat
                                 db urakka-id sopimus-id (:paallystyskohde-id %))))))
        (harja.kyselyt.paallystys/hae-urakan-paallystysilmoitukset db urakka-id sopimus-id)))