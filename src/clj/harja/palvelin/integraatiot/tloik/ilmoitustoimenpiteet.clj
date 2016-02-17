(ns harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma :as toimenpide-sanoma]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.util UUID)))

(defn laheta [jms-lahettaja db id]
  (let [viesti-id (str (UUID/randomUUID))
        data (konversio/alaviiva->rakenne (first (ilmoitukset/hae-ilmoitustoimenpide db id)))
        xml (toimenpide-sanoma/muodosta data viesti-id)]
    (if xml
      (do
        (jms-lahettaja xml viesti-id)
        (ilmoitukset/merkitse-ilmoitustoimenpide-odottamaan-vastausta! db viesti-id id)
        (when (= "lopetus" (:kuittaustyyppi data))
          (ilmoitukset/merkitse-ilmoitustoimenpide-suljetuksi! db (:ilmoitus data)))
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
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db id)
      (throw e))))

(defn vastaanota-kuittaus [db viesti-id onnistunut]
  (if onnistunut
    (do
      (log/debug (format "Ilmoitustoimenpide kuitattiin T-LOIK:sta onnistuneeksi viesti-id:llä: %s" viesti-id))
      (ilmoitukset/merkitse-ilmoitustoimenpide-lahetetyksi! db viesti-id))

    (do
      (log/error (format "Ilmoitustoimenpide kuitattiin T-LOIK:sta epäonnistuneeksi viesti-id:llä: %s" viesti-id))
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db viesti-id))))

(defn tallenna-ilmoitustoimenpide [db ilmoitus vapaateksti toimenpide paivystaja]
  (:id (ilmoitukset/luo-ilmoitustoimenpide<!
         db
         (:id ilmoitus)
         (:ilmoitusid ilmoitus)
         (pvm/nyt)
         vapaateksti
         toimenpide
         (:etunimi paivystaja)
         (:sukunimi paivystaja)
         (:tyopuhelin paivystaja)
         (:matkapuhelin paivystaja)
         (:sahkoposti paivystaja)
         (:nimi paivystaja)
         (:ytunnus paivystaja)
         nil
         nil
         nil
         nil
         nil
         nil
         nil)))

(defn vastaanota-sahkopostikuittaus [db viesti]
  ;; PENDING: viestien käsittely toteutettava,
  ;; ks. otsikosta esim. pattern #ur/ilm, jossa urakan ja ilmoituksen id
  ;; bodysta haetaan onko kyseessä minkä tyyppinen kuittaus

  (log/debug (format "Vastaanotettiin T-LOIK kuittaus sähköpostilla. Viesti: %s." viesti))
  nil)