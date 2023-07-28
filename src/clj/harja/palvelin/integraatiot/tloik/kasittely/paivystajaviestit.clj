(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik.sahkoposti :as tloik-sahkoposti]
            [harja.palvelin.integraatiot.tloik.tekstiviesti :as tloik-tekstiviesti]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet])
  (:use [slingshot.slingshot :only [try+]]))

(defn laheta-ilmoitus-sahkopostilla [{:keys [email]} db {id :ilmoitus-id :as ilmoitus} {vastaanottaja :sahkoposti :as paivystaja}]
  ;; Lähetetään vain vastaanottajalle, jolla on jonkinlainen emailosoite annettuna
  (if (and vastaanottaja (> (count vastaanottaja) 3))
    (do
      (log/info "Lähetetään ilmoitus (id: " id ") sähköpostilla osoitteeseen: " vastaanottaja)
      (let [lahettaja (sahkoposti/vastausosoite email)
            [otsikko viesti] (tloik-sahkoposti/otsikko-ja-viesti db lahettaja ilmoitus)]
        (try
         (sahkoposti/laheta-viesti! email lahettaja vastaanottaja otsikko viesti {"X-Correlation-ID" id})
         (ilmoitustoimenpiteet/tallenna-ilmoitustoimenpide
           db
           (:id ilmoitus)
           (:ilmoitus-id ilmoitus)
           viesti
           "valitys"
           paivystaja
           "ulos"
           "sahkoposti")
         (catch Exception e
           (log/error (format "Ilmoituksen %s lähettämisessä sähköpostilla tapahtui poikkeus." (:ilmoitus-id ilmoitus)) e)))))
    (log/warn (format "Ilmoitusta %s ei voida lähettää sähköpostilla ilman sähköpostiosoitetta." (:ilmoitus-id ilmoitus)))))

(defn laheta-ilmoitus-tekstiviestilla [sms db ilmoitus paivystaja]
  (tloik-tekstiviesti/laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))

(defn laheta [{sms :sms :as ilmoitusasetukset} db ilmoitus paivystaja]
  (when (and sms
             (:vastuuhenkilo paivystaja)
             (= "toimenpidepyynto" (:ilmoitustyyppi ilmoitus)))
    (laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))
  (laheta-ilmoitus-sahkopostilla ilmoitusasetukset db ilmoitus paivystaja))
