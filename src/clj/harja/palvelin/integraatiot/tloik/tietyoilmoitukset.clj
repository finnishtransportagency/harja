(ns harja.palvelin.integraatiot.tloik.tietyoilmoitukset
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.domain.tietyoilmoitus :as tietyoilmoitus-d]
            [harja.domain.tierekisteri :as tierekisteri-d]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.palvelin.integraatiot.tloik.sanomat.tietyoilmoitussanoma :as tietyoilmoitussanoma]
            [harja.kyselyt.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.palvelut.tierekisteri-haku :as tierekisteri])
  (:import (java.util UUID)))

(defn laske-pituus [db tietyoilmoitus]
  (let [osoite (::tietyoilmoitus-d/osoite tietyoilmoitus)
        osien-pituudet (tierekisteri/hae-osien-pituudet db {:tie (::tierekisteri-d/tie osoite)
                                                            :aosa (::tierekisteri-d/aosa osoite)
                                                            :losa (::tierekisteri-d/losa osoite)})]
    (tierekisteri-d/laske-tien-pituus osien-pituudet {:tr-alkuosa (::tierekisteri-d/aosa osoite)
                                                      :tr-alkuetaisyys (::tierekisteri-d/aet osoite)
                                                      :tr-loppuosa (::tierekisteri-d/losa osoite)
                                                      :tr-loppuetaisyys (::tierekisteri-d/let osoite)})))

(defn hae [db id]
  (let [karttapvm (geometriapaivitykset/harjan-verkon-pvm db)
        tietyoilmoitus (tietyoilmoitukset/hae-ilmoitus db id)
        pituus (laske-pituus db tietyoilmoitus)
        tietyoilmoitus (assoc tietyoilmoitus
                         :karttapvm karttapvm
                         :pituus pituus)]
    tietyoilmoitus))

(defn laheta [jms-lahettaja db id]
  (let [viesti-id (str (UUID/randomUUID))
        tietyoilmoitus (hae db id)
        muodosta-xml #(tietyoilmoitussanoma/muodosta tietyoilmoitus viesti-id)]
    (try
      (jms-lahettaja muodosta-xml viesti-id)
      (tietyoilmoitukset/lisaa-tietyoilmoituksen-email-lahetys! db {:id id :lahetysid viesti-id})
      (log/debug (format "Tietyöilmoituksen (id: %s) lähetys T-LOIK:n onnistui." id))
      (catch Exception e
        (log/error e (format "Tietyöilmoituksen (id: %s) lähetys T-LOIK:n epäonnistui." id))
        (tietyoilmoitukset/merkitse-tietyoilmoitukselle-lahetysvirhe! db {:id id})))))

(defn laheta-tietyoilmoitus [jms-lahettaja db id]
  (log/debug (format "Lähetetään tietyöilmoitus (id: %s) T-LOIK:n." id))
  (try
    (laheta jms-lahettaja db id)
    (catch Exception e
      (log/error e (format "Tietyöilmoituksen (id: %s) lähetyksessä T-LOIK:n tapahtui poikkeus." id))
      (tietyoilmoitukset/merkitse-tietyoilmoitukselle-lahetysvirhe! db {:id id}))))

(defn vastaanota-kuittaus [db viesti-id onnistunut]
  (if onnistunut
    (do
      (log/debug (format "Tietyöilmoitus kuitattiin T-LOIK:sta onnistuneeksi viesti-id:llä: %s" viesti-id))
      (tietyoilmoitukset/merkitse-tietyoilmoituksen-sahkopostilahetys-kuitatuksi! db {:lahetysid viesti-id}))

    (do
      (log/error (format "Tietyöilmoitus kuitattiin T-LOIK:sta epäonnistuneeksi viesti-id:llä: %s" viesti-id))
      (tietyoilmoitukset/merkitse-tietyoilmoitukselle-lahetysvirhe-lahetysidlla! db {:lahetysid viesti-id}))))