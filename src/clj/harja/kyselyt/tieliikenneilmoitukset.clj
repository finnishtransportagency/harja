(ns harja.kyselyt.tieliikenneilmoitukset
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tieliikenneilmoitukset.sql"
            {:positional? true})

(defn ilmoitukselle-olemassa-vastaanottokuittaus? [db ilmoitusid]
  (not (empty? (onko-ilmoitukselle-vastaanottokuittausta db ilmoitusid))))

(defn ilmoitus-loytyy-idlla? [db ilmoitusid]
  (:exists (first (ilmoitus-loytyy-idlla db ilmoitusid))))

(defn ilmoitus-on-lahetetty-urakalle? [db ilmoitusid urakkaid]
  (:exists (first (ilmoitus-on-lahetetty-urakalle db {:ilmoitusid ilmoitusid
                                                      :urakkaid urakkaid}))))

(defn paivita-ilmoituksen-urakka [db ilmoitusid urakkaid]
  (paivita-ilmoituksen-urakka! db ilmoitusid urakkaid))
