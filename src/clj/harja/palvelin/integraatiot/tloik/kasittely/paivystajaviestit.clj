(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit])
  (:use [slingshot.slingshot :only [try+]]))

(def +ilmoitustekstiviesti+
  (str "Uusi toimenpidepyyntö: %s (id: %s). \n\n"
       "%s\n\n"
       "Selitteet: %s.\n\n"
       "Vastaa viestiin: \n"
       "V%s = vastaanotettu, \n"
       "A%s = aloitettu, \n"
       "L%s = lopetettu."))

(defn laheta-ilmoitus-tekstiviestilla [sms db ilmoitus paivystaja]
  (try
    (if-let [puhelinnumero (or (:matkapuhelin paivystaja) (:tyopuhelin paivystaja))]
      (log/debug (format "Lähetetään ilmoitus (id: %s) tekstiviestillä numeroon: %s" (:ilmoitus-id ilmoitus) puhelinnumero))
      (let [paivystaja-id (:id paivystaja)
            ilmoitus-id (:ilmoitus-id ilmoitus)
            otsikko (:otsikko ilmoitus)
            lyhytselite (:lyhytselite ilmoitus)
            selitteet (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))
            viestinumero (paivystajatekstiviestit/hae-seuraava-viestinumero db paivystaja-id)
            viesti (format +ilmoitustekstiviesti+
                           otsikko
                           ilmoitus-id
                           lyhytselite
                           selitteet
                           viestinumero
                           viestinumero
                           viestinumero)]
        (sms/laheta sms puhelinnumero viesti)
        (paivystajatekstiviestit/kirjaa-uusi-paivystajatekstiviesti! db viestinumero ilmoitus-id paivystaja-id))
      (log/warn "Ilmoitusta ei voida lähettää tekstiviestillä ilman puhelinnumeroa."))
    (catch Exception e
      (log/error "Ilmoituksen lähettämisessä tekstiviestillä tapahtui poikkeus." e))))

(defn laheta [sms db ilmoitus paivystaja]
  (when (and (= "toimenpidepyynto" (:ilmoitustyyppi ilmoitus)) sms)
    (laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))
  ;; todo: laheta sähköpostilla
  )