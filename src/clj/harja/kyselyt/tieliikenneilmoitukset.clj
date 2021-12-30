(ns harja.kyselyt.tieliikenneilmoitukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tieliikenneilmoitukset.sql"
            {:positional? true})

(defn ilmoitukselle-olemassa-vastaanottokuittaus? [db ilmoitusid]
  (not (empty? (onko-ilmoitukselle-vastaanottokuittausta db ilmoitusid))))

(defn ilmoitus-loytyy-idlla? [db ilmoitusid]
  (:exists (first (ilmoitus-loytyy-idlla db ilmoitusid))))
