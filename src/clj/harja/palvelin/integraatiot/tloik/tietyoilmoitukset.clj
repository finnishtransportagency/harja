(ns harja.palvelin.integraatiot.tloik.tietyoilmoitukset
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.tietyoilmoitukset :as tietyoilmoitukset]))

(defn laheta [jms-lahettaja db id]
  )

(defn laheta-tietyoilmoitus [jms-lahettaja db id]
  (log/debug (format "Lähetetään tietyöilmoitus (id: %s) T-LOIK:n." id))
  (try
    (laheta jms-lahettaja db id)
    (catch Exception e
      (log/error e (format "Tietyöilmoituksen (id: %s) lähetyksessä T-LOIK:n tapahtui poikkeus." id))
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe-idlla! db id)
      (throw e)))
  )
