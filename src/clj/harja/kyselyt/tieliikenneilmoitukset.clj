(ns harja.kyselyt.tieliikenneilmoitukset
  (:require [jeesql.core :refer [defqueries]]))

(declare hae-ilmoitukset onko-ilmoitukselle-vastaanottokuittausta ilmoitus-loytyy-idlla ilmoitus-on-lahetetty-urakalle
  paivita-ilmoituksen-urakka! hae-ilmoitukset-asiakaspalauteluokittain hae-ilmoitukset-raportille
  hae-ilmoituskuittausten-urakat hae-ilmoitus luo-ilmoitustoimenpide<! ilmoitus-aiheutti-toimenpiteita!
  hae-ilmoitukset-idlla peruuta-ilmoitusten-toimenpiteiden-aloitukset! tallenna-ilmoitusten-toimenpiteiden-aloitukset!)

(defqueries "harja/kyselyt/tieliikenneilmoitukset.sql"
  {:positional? true})

(defn ilmoitukselle-olemassa-vastaanottokuittaus? [db ilmoitusid]
  (boolean (seq (onko-ilmoitukselle-vastaanottokuittausta db ilmoitusid))))

(defn ilmoitus-loytyy-idlla? [db ilmoitusid]
  (:exists (first (ilmoitus-loytyy-idlla db ilmoitusid))))

(defn ilmoitus-on-lahetetty-urakalle? [db ilmoitusid urakkaid]
  (:exists (first (ilmoitus-on-lahetetty-urakalle db {:ilmoitusid ilmoitusid
                                                      :urakkaid urakkaid}))))

(defn paivita-ilmoituksen-urakka [db ilmoitusid urakkaid]
  (paivita-ilmoituksen-urakka! db ilmoitusid urakkaid))
