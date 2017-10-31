(ns harja.kyselyt.tieliikenneilmoitukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tieliikenneilmoitukset.sql"
            {:positional? true})

(defn ilmoitukselle-olemassa-vastaanottokuittaus? [db ilmoitusid]
  (not (empty? (onko-ilmoitukselle-vastaanottokuittausta db ilmoitusid))))

(defn ilmoitus-valitetty-paivystajille? [db ilmoitusid]
  (:exists (first (onko-ilmoitus-valitetty-paivystajille? db ilmoitusid))))
