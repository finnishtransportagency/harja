(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik.sahkoposti :as tloik-sahkoposti])
  (:use [slingshot.slingshot :only [try+]]))

(def +ilmoitustekstiviesti+
  (str "Uusi toimenpidepyyntö: %s (id: %s, viestinumero: %s).\n\n"
       "%s\n\n"
       "Selitteet: %s.\n\n"
       "Voit kirjata uuden toimenpiteen, antamalla toimenpiteen lyhenteen ja viestinumeron:\n"
       "V%s = vastaanotettu\n"
       "A%s = aloitettu\n"
       "L%s = lopetettu"))

(defn laheta-ilmoitus-tekstiviestilla [sms db ilmoitus paivystaja]
  (try
    (if-let [puhelinnumero (or (:matkapuhelin paivystaja) (:tyopuhelin paivystaja))]
      (do
        (log/debug (format "Lähetetään ilmoitus (id: %s) tekstiviestillä numeroon: %s" (:ilmoitus-id ilmoitus) puhelinnumero))
        (let [paivystaja-id (:id paivystaja)
              ilmoitus-id (:ilmoitus-id ilmoitus)
              otsikko (:otsikko ilmoitus)
              lyhytselite (:lyhytselite ilmoitus)
              selitteet (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))
              viestinumero (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id ilmoitus-id)
              viesti (format +ilmoitustekstiviesti+
                             otsikko
                             ilmoitus-id
                             viestinumero
                             lyhytselite
                             selitteet
                             viestinumero
                             viestinumero
                             viestinumero)]
          (sms/laheta sms puhelinnumero viesti)))
      (log/warn "Ilmoitusta ei voida lähettää tekstiviestillä ilman puhelinnumeroa."))
    (catch Exception e
      (log/error "Ilmoituksen lähettämisessä tekstiviestillä tapahtui poikkeus." e))))

(defn laheta-ilmoitus-sahkopostilla [email db {id :ilmoitus-id :as ilmoitus} {vastaanottaja :sahkoposti :as paivystaja}]
  (if vastaanottaja
    (do
      (log/debug "Lähetetään ilmoitus (id: %s) sähköpostilla osoitteeseen: %s" id vastaanottaja)
      (let [lahettaja (sahkoposti/vastausosoite email)
            [otsikko viesti] (tloik-sahkoposti/otsikko-ja-viesti lahettaja ilmoitus)]
        (sahkoposti/laheta-viesti! email lahettaja vastaanottaja otsikko viesti)))
    (log/warn "Ilmoitusta ei voida lähettää sähköpostilla ilman sähköpostiosoitetta.")))

(defn laheta [sms email db ilmoitus paivystaja]

  (when (and (= "toimenpidepyynto" (:ilmoitustyyppi ilmoitus)) sms)
    (laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))
  (laheta-ilmoitus-sahkopostilla email db ilmoitus paivystaja))
