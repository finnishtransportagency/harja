(ns harja.palvelin.integraatiot.tloik.ilmoitukset
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as time]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitus-sanoma]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma :as kuittaus]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot]
            [harja.kyselyt.urakat :as urakat]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.tyokalut.xml :as xml]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.kyselyt.paivystajaviestit :as paivystajaviestit])
  (:use [slingshot.slingshot :only [try+]]))

(def +xsd-polku+ "xsd/tloik/")

(def +ilmoitustekstiviesti+
  (str "Uusi toimenpidepyyntö: %s (id: %s). \n\n"
       "%s\n\n"
       "Selitteet: %s.\n\n"
       "Vastaa viestiin: \n"
       "V%s = vastaanotettu, \n"
       "A%s = aloitettu, \n"
       "L%s = lopetettu."))

(defn laheta-ilmoitus-tekstiviestilla [sms db ilmoitus paivystaja]
  ;; todo: mieti miten hanskataan poikkeukset, jos lähetys ei onnistu
  (if-let [puhelinnumero (or (:matkapuhelin paivystaja) (:tyopuhelin paivystaja))]
    (let [paivystaja-id (:id paivystaja)
          ilmoitus-id (:ilmoitus-id ilmoitus)
          otsikko (:otsikko ilmoitus)
          lyhytselite (:lyhytselite ilmoitus)
          selitteet (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))
          viestinumero (paivystajaviestit/hae-seuraava-viestinumero db paivystaja-id)
          viesti (format +ilmoitustekstiviesti+
                         otsikko
                         ilmoitus-id
                         lyhytselite
                         selitteet
                         viestinumero
                         viestinumero
                         viestinumero)]
      (sms/laheta sms puhelinnumero viesti)

      (paivystajaviestit/kirjaa-uusi-paivystajaviesti! db viestinumero ilmoitus-id paivystaja-id)
      ;; todo: hae uusi id kannasta
      ;; todo: kirjaa uusi päivystäjäviesti kantaan
      )
    (log/warn "Ilmoitusta ei voida lähettää tekstiviestillä ilman puhelinnumeroa.")))

(defn laheta-paivystajalle [sms db ilmoitus urakka-id]
  (let [paivystaja (yhteyshenkilot/hae-urakan-tamanhetkinen-paivystaja db urakka-id)]
    (if paivystaja
      (do
        (when (and (= "toimenpidepyynto" (:ilmoitustyyppi ilmoitus)) sms)
          (laheta-ilmoitus-tekstiviestilla sms db ilmoitus paivystaja))
        ;; todo: laheta sähköpostilla
        paivystaja)
      ;; todo: mieti miten toimitaan, jos päivystäjää ei löydy. riittääkö palauttaa vain tieto t-loik:n.
      )))

(defn laheta-kuittaus [sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id onnistunut lisatietoja]
  (lokittaja :lahteva-jms-kuittaus kuittaus tapahtuma-id onnistunut lisatietoja)
  (sonja/laheta sonja kuittausjono kuittaus {:correlation-id korrelaatio-id}))

(defn vastaanota-ilmoitus [sonja lokittaja sms tapahtumat db kuittausjono viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [jms-viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        korrelaatio-id (.getJMSCorrelationID viesti)
        tapahtuma-id (lokittaja :saapunut-jms-viesti jms-viesti-id viestin-sisalto)]

    (if (xml/validoi +xsd-polku+ "harja-tloik.xsd" (.getText viesti))
      (let [{:keys [viesti-id ilmoitus-id] :as ilmoitus} (ilmoitus-sanoma/lue-viesti viestin-sisalto)]
        (try+
          (let [urakka-id (jdbc/with-db-transaction [transaktio db] (ilmoitus/kasittele-ilmoitus transaktio ilmoitus))
                urakka (first (urakat/hae-urakka db urakka-id))]
            (laheta-paivystajalle sms db ilmoitus urakka-id)
            ;; todo: liitä paivystäjätiedot kuittauksen välitystietoihin
            (notifikaatiot/ilmoita-saapuneesta-ilmoituksesta tapahtumat urakka-id ilmoitus-id)
            (notifikaatiot/kun-ilmoitus-lahetetty
              tapahtumat ilmoitus-id
              (fn [valitystapa]
                (laheta-kuittaus sonja lokittaja kuittausjono
                                 (kuittaus/muodosta viesti-id ilmoitus-id (time/now) "valitetty" urakka nil nil)
                                 korrelaatio-id tapahtuma-id true
                                 (str "Välitystapa: " valitystapa)))))

          (catch [:type virheet/+urakkaa-ei-loydy+] {:keys [virheet]}
            (let [virhe (format "Urakkaa ei voitu päätellä T-LOIK:n ilmoitukselle (id: %s, viesti id: %s)" ilmoitus-id viesti-id)
                  kuittaus (kuittaus/muodosta viesti-id ilmoitus-id (.toString (time/now)) "virhe" nil nil "Tiedoilla ei voitu päätellä urakkaa.")]
              (log/error virhe)
              (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id false virhe)))

          (catch Exception e
            (log/error e (format "Tapahtui poikkeus luettaessa sisään ilmoitusta T-LOIK:sta (id: %s, viesti id: %s)" ilmoitus-id viesti-id))
            (let [virhe (str (format "Poikkeus (id: %s, viesti id: %s) " ilmoitus-id viesti-id) e)
                  kuittaus (kuittaus/muodosta viesti-id ilmoitus-id (.toString (time/now)) "virhe" nil nil "Sisäinen käsittelyvirhe.")]
              (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id false virhe)))))

      (let [virhe "XML-sanoma ei ole harja-tloik.xsd skeeman mukainen."
            kuittaus (kuittaus/muodosta "-" nil (.toString (time/now)) "virhe" nil nil virhe)]
        (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id false virhe)))))
