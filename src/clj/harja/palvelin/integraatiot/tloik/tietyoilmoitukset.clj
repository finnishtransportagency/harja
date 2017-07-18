(ns harja.palvelin.integraatiot.tloik.tietyoilmoitukset
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.palvelin.integraatiot.tloik.sanomat.tietyoilmoitussanoma :as tietyoilmoitussanoma]
            [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset])
  (:import (java.util UUID)))

(defn laheta [jms-lahettaja db id]
  (let [viesti-id (str (UUID/randomUUID))
        uusi? (not (tietyoilmoitukset/lahetetty? db id))
        karttapvm (geometriapaivitykset/harjan-verkon-pvm db)
        tietyoilmoitus (tietyoilmoitukset/hae-ilmoitus db id)
        tietyoilmoitus (assoc tietyoilmoitus
                         :uusi? uusi?
                         :karttapvm karttapvm)
        muodosta-xml #(tietyoilmoitussanoma/muodosta tietyoilmoitus viesti-id)]
    (try
      (jms-lahettaja muodosta-xml viesti-id)
      (tietyoilmoitukset/merkitse-tietyoilmoitus-odottamaan-vastausta! db viesti-id id)
      (log/debug (format "Tietyöilmoituksen (id: %s) lähetys T-LOIK:n onnistui." id))
      (catch Exception e
        (log/error e (format "Tietyöilmoituksen (id: %s) lähetys T-LOIK:n epäonnistui." id))
        (tietyoilmoitukset/merkitse-tietyoilmoitukselle-lahetysvirhe! db id)))))

(defn laheta-tietyoilmoitus [jms-lahettaja db id]
  (log/debug (format "Lähetetään tietyöilmoitus (id: %s) T-LOIK:n." id))
  (try
    (laheta jms-lahettaja db id)
    (catch Exception e
      (log/error e (format "Tietyöilmoituksen (id: %s) lähetyksessä T-LOIK:n tapahtui poikkeus." id))
      (tietyoilmoitukset/merkitse-tietyoilmoitukselle-lahetysvirhe! db id)
      (throw e))))

(defn laheta-lahettamattomat-tietyoilmoitukset [tietyoilmoitus-jms-lahettaja db]
  (lukko/yrita-ajaa-lukon-kanssa
    db
    "tloik-tti-uudelleenlahetys"
    #(do
       (log/debug "Lähetetään lähettämättömät tietyöilmoitukset T-LOIK:n.")
       (let [idt (mapv :id (tietyoilmoitukset/hae-lahettamattomat-tietyoilmoitukset db))]
         (doseq [id idt]
           (try
             (laheta-tietyoilmoitus tietyoilmoitus-jms-lahettaja db id)
             (catch Exception _))))
       (log/debug "Tietyöilmoitusten lähettäminen T-LOIK:n valmis."))))
