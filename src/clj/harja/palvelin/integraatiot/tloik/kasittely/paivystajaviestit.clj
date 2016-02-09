(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.domain.ilmoitusapurit :as apurit]))


(defn laheta-ilmoitus-tekstiviestilla [sms ilmoitus puhelinnumero]
  ;; todo: mieti miten hanskataan poikkeukset, jos lähetys ei onnistu
  (if puhelinnumero
    (let [viesti (format "Uusi toimenpidepyyntö (%s): %s: %s. Selitteet: (%s). Vastaa viestiin: V = vastaanotettu, A = aloitettu, L = lopetettu."
                         (:ilmoitus-id ilmoitus)
                         (:otsikko ilmoitus)
                         (:lyhytselite ilmoitus)
                         ;; todo: parsi selitteet
                         (:selitteet ilmoitus))]
      (sms/laheta sms puhelinnumero viesti))
    (log/warn "Ilmoitusta ei voida lähettää tekstiviestillä ilman puhelinnumeroa.")))

(defn laheta-paivystajalle [sms db ilmoitus urakka-id]
  (let [paivystaja (yhteyshenkilot/hae-urakan-tamanhetkinen-paivystaja db urakka-id)
        puhelinnumero (or (:matkapuhelin paivystaja) (:tyopuhelin paivystaja))]
    (if paivystaja
      (do
        (when (and (= "toimenpidepyynto" (:ilmoitustyyppi ilmoitus)) sms)
          (laheta-ilmoitus-tekstiviestilla sms ilmoitus puhelinnumero))
        ;; todo: laheta sähköpostilla
        paivystaja)
      ;; todo: mieti miten toimitaan, jos päivystäjää ei löydy. riittääkö palauttaa vain tieto t-loik:n.
      )))