(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.domain.ilmoitusapurit :as apurit]))


(defn laheta-ilmoitus-tekstiviestilla [sms ilmoitus paivystaja]
  ;; todo: mieti miten hanskataan poikkeukset, jos lähetys ei onnistu
  (if-let [puhelinnumero (or (:matkapuhelin paivystaja) (:tyopuhelin paivystaja))]
    (let [viesti (format "Uusi toimenpidepyyntö (%s): %s: %s. Selitteet: (%s). Vastaa viestiin: V = vastaanotettu, A = aloitettu, L = lopetettu."
                         (:ilmoitus-id ilmoitus)
                         (:otsikko ilmoitus)
                         (:lyhytselite ilmoitus)
                         ;; todo: parsi selitteet
                         (:selitteet ilmoitus))]
      (sms/laheta sms puhelinnumero viesti)
      ;; todo: kirjaa uusi päivystäjäviesti kantaan
      )
    (log/warn "Ilmoitusta ei voida lähettää tekstiviestillä ilman puhelinnumeroa.")))

(defn laheta-paivystajalle [sms db ilmoitus urakka-id]
  (let [paivystaja (yhteyshenkilot/hae-urakan-tamanhetkinen-paivystaja db urakka-id)]
    (if paivystaja
      (do
        (when (and (= "toimenpidepyynto" (:ilmoitustyyppi ilmoitus)) sms)
          (laheta-ilmoitus-tekstiviestilla sms ilmoitus paivystaja))
        ;; todo: laheta sähköpostilla
        paivystaja)
      ;; todo: mieti miten toimitaan, jos päivystäjää ei löydy. riittääkö palauttaa vain tieto t-loik:n.
      )))