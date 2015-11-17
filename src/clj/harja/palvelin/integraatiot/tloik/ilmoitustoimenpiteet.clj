(ns harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma :as tloik-kuittaus-sanoma]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma :as toimenpide-sanoma]
            [harja.palvelin.tyokalut.lukot :as lukko])
  (:import (java.util UUID)))

(defn laheta [jms-lahettaja db id]
  (log/debug (format "Lähetetään ilmoitustoimenpide (id: %s) T-LOIK:n" id))
  (let [viesti-id (str (UUID/randomUUID))
        data (konversio/alaviiva->rakenne (first (ilmoitukset/hae-ilmoitustoimenpide db id)))
        xml (toimenpide-sanoma/muodosta data viesti-id)]
    (if xml
      (do
        (jms-lahettaja xml viesti-id)
        (ilmoitukset/merkitse-ilmoitustoimenpide-odottamaan-vastausta! db viesti-id id)
        (log/debug (format "Ilmoitustoimenpiteen (id: %s) lähetys T-LOIK:n onnistui." id)))
      (do
        (log/error (format "Ilmoitustoimenpiteen (id: %s) lähetys T-LOIK:n epäonnistui." id))
        (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db id)))))

(defn laheta-ilmoitustoimenpide [jms-lahettaja db id]
  (log/debug (format "Lähetetään ilmoitustoimenpide (id: %s) T-LOIK:n." id))
  (try
    (lukko/aja-lukon-kanssa db "tloik-ilm.toimenpidelahetys" (fn [] (laheta jms-lahettaja db id)))
    (catch Exception e
      (log/error e (format "Ilmoitustoimenpiteen (id: %s) lähetyksessä T-LOIK:n tapahtui poikkeus." id))
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db id))))

(defn vastaanota-kuittaus [lokittaja db viesti]
  (log/debug (format "Vastaanotettiin ilmoitustoimenpiteelle T-LOIK:sta ilmoitustoimenpiteelle kuittaus: %s " viesti))
  (let [kuittaus-xml (.getText viesti)
        kuittaus (tloik-kuittaus-sanoma/lue-kuittaus kuittaus-xml)
        onnistunut? (not (contains? kuittaus :virhe))]
    (log/debug (format "Kuittauksen sisältö: %s" kuittaus))
    (if-let [viesti-id (:viesti-id kuittaus)]
      (do
        (lokittaja :saapunut-jms-kuittaus viesti-id kuittaus-xml onnistunut?)
        (when onnistunut? (ilmoitukset/merkitse-ilmoitustoimenpide-lahetetyksi! db viesti-id)))
      (log/error "Kuittauksesta ei voitu hakea viesti-id:tä."))))