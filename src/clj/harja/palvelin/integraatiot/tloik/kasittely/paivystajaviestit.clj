(ns harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.domain.ilmoitusapurit :as apurit]))


(def +ilmoitustekstiviesti+ "Uusi toimenpidepyyntö: %s (id: %s). \n\n%s\n\nSelitteet: %s.\n\nVastaa viestiin: \nV = vastaanotettu, \nA = aloitettu, \nL = lopetettu.")

(defn laheta-ilmoitus-tekstiviestilla [sms ilmoitus paivystaja]
  ;; todo: mieti miten hanskataan poikkeukset, jos lähetys ei onnistu
  (if-let [puhelinnumero (or (:matkapuhelin paivystaja) (:tyopuhelin paivystaja))]
    (let [selitteet (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))
          viesti (format +ilmoitustekstiviesti+
                         (:otsikko ilmoitus)
                         (:ilmoitus-id ilmoitus)
                         (:lyhytselite ilmoitus)
                         selitteet)]
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