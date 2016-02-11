(ns harja.palvelin.integraatiot.tloik.kuittaukset
  "Ilmoitusten kuittausten vastaanottaminen sähköpostin ja tekstiviestien kautta"
  (:require [taoensso.timbre :as log]))

(defn vastaanota-sahkopostikuittaus [db viesti]
  ;; PENDING: viestien käsittely toteutettava,
  ;; ks. otsikosta esim. pattern #ur/ilm, jossa urakan ja ilmoituksen id
  ;; bodysta haetaan onko kyseessä minkä tyyppinen kuittaus

  (log/debug (format "Vastaanotettiin T-LOIK kuittaus sähköpostilla. Viesti: %s." viesti))
  nil)

(defn vastaanota-tekstiviestikuittaus [db numero viesti]
  (log/debug (format "Vastaanotettiin T-LOIK kuittaus tekstiviestillä. Numero: %s, viesti: %s." numero viesti)))
