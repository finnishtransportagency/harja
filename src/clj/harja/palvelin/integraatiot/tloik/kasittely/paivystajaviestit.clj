(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik.sahkoposti :as tloik-sahkoposti]
            [harja.palvelin.integraatiot.tloik.tekstiviesti :as tloik-tekstiviesti])
  (:use [slingshot.slingshot :only [try+]]))

(defn laheta-ilmoitus-sahkopostilla [{:keys [email google-static-maps-key]} {id :ilmoitus-id :as ilmoitus} {vastaanottaja :sahkoposti :as paivystaja}]
  (if vastaanottaja
    (do
      (log/debug "Lähetetään ilmoitus (id: %s) sähköpostilla osoitteeseen: %s" id vastaanottaja)
      (let [lahettaja (sahkoposti/vastausosoite email)
            [otsikko viesti] (tloik-sahkoposti/otsikko-ja-viesti lahettaja ilmoitus google-static-maps-key)]
        (sahkoposti/laheta-viesti! email lahettaja vastaanottaja otsikko viesti)))
    (log/warn "Ilmoitusta ei voida lähettää sähköpostilla ilman sähköpostiosoitetta.")))

(defn laheta-ilmoitus-tekstiviestilla [sms db ilmoitus paivystaja]
  (tloik-tekstiviesti/laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))

(defn laheta [{sms :sms :as ilmoitusasetukset} db ilmoitus paivystaja]
  (when (and (= "toimenpidepyynto" (:ilmoitustyyppi ilmoitus)) sms)
    (laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))
  (laheta-ilmoitus-sahkopostilla ilmoitusasetukset ilmoitus paivystaja))
