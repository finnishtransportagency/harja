(ns harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma :as tloik-kuittaus-sanoma]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.tloik.sanomat.toimenpide-sanoma :as toimenpide-sanoma]))

(defn laheta-ilmoitustoimenpide [jms-lahettaja db id]
  (log/debug (format "Lähetetään ilmoitustoimenpide (id: %s)." id))
  (try
    (let [data (konversio/alaviiva->rakenne (ilmoitukset/hae-ilmoitustoimenpide db id))
          xml (toimenpide-sanoma/muodosta data)]
      (if xml
        (do
          (jms-lahettaja xml)
          (ilmoitukset/merkitse-ilmoitustoimenpide-odottamaan-vastausta! db id)
          (log/debug (format "Ilmoitustoimenpiteen (id: %s) lähetys onnistui." id)))
        (do
          (log/error (format "Ilmoitustoimenpiteen (id: %s) lähetys epäonnistui." id))
          (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db id))))
    (catch Exception e
      (log/error e (format "Ilmoitustoimenpiteen (id: %s) lähetyksessä tapahtui poikkeus." id))
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db id))))

(defn vastaanota-kuittaus [integraatioloki db viesti]
  (log/debug (format "Vastaanotettiin ilmoitustoimenpiteelle kuittaus: %s " viesti))
  (let [kuittaus-xml (.getText viesti)]
    (let [kuittaus (tloik-kuittaus-sanoma/lue-kuittaus kuittaus-xml)
          onnistunut (not (contains? kuittaus :virhe))]
      (log/debug "Luettiin kuittaus T-LOIK:sta: " kuittaus)
      (if-let [viesti-id (:viesti-id kuittaus)]
        (do
          (integraatioloki/kirjaa-saapunut-jms-kuittaus integraatioloki kuittaus-xml viesti-id "toimenpiteen-lahetys" onnistunut)
          (ilmoitukset/merkitse-ilmoitustoimenpide-lahetetyksi! db viesti-id))
        (log/error "Kuittauksesta ei voitu hakea viesti-id:tä.")))))