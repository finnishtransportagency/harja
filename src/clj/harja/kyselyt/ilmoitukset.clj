(ns harja.kyselyt.ilmoitukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/ilmoitukset.sql"
            {:positional? true})

(defn ilmoitukselle-olemassa-vastaanottokuittaus? [db ilmoitusid]
  (not (empty? (onko-ilmoitukselle-vastaanottokuittausta db ilmoitusid))))
