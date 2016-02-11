(ns harja.palvelin.integraatiot.tloik.ilmoitukset
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as time]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitus-sanoma]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma :as kuittaus-sanoma]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot]
            [harja.kyselyt.urakat :as urakat]
            [harja.tyokalut.xml :as xml]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit :as paivystajaviestit]
            [harja.palvelin.palvelut.urakat :as urakkapalvelu])
  (:use [slingshot.slingshot :only [try+]]))

(def +xsd-polku+ "xsd/tloik/")

(defn laheta-paivystajalle [sms db ilmoitus urakka-id]
  (when-let [paivystaja (yhteyshenkilot/hae-urakan-tamanhetkinen-paivystaja db urakka-id)]
    (paivystajaviestit/laheta sms db ilmoitus paivystaja)
    paivystaja))

(defn laheta-kuittaus [sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id onnistunut lisatietoja]
  (lokittaja :lahteva-jms-kuittaus kuittaus tapahtuma-id onnistunut lisatietoja)
  (sonja/laheta sonja kuittausjono kuittaus {:correlation-id korrelaatio-id}))

(defn lue-ilmoitus [sonja lokittaja kuittausjono korrelaatio-id tapahtuma-id viesti]
  (if (xml/validoi +xsd-polku+ "harja-tloik.xsd" (.getText viesti))
    (ilmoitus-sanoma/lue-viesti viesti)
    (let [virhe "XML-sanoma ei ole harja-tloik.xsd skeeman mukainen."
          kuittaus (kuittaus-sanoma/muodosta "-" nil (.toString (time/now)) "virhe" nil nil virhe)]
      (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id false virhe))))

(defn hae-urakka [db ilmoitus]
  (when-let [urakka-id (first (urakkapalvelu/hae-urakka-idt-sijainnilla db (:urakkatyyppi ilmoitus) (:sijainti ilmoitus)))]
    (urakka (first (urakat/hae-urakka db urakka-id)))))

(defn kasittele-ilmoitus [sms db tapahtumat urakka ilmoitus]
  (let [urakka-id (:id urakka)
        ilmoitus-id (:ilmoitus-id ilmoitus)]
    (ilmoitus/tallenna-ilmoitus db ilmoitus)
    (let [paivystaja (laheta-paivystajalle sms db ilmoitus urakka-id)]
      (notifikaatiot/ilmoita-saapuneesta-ilmoituksesta tapahtumat urakka-id ilmoitus-id)
      ;; todo: ack pitäisi varmaan lähettää suoraan samantien eikä odottaa, että ilmoitus on välitetty api:n kautta?
      (notifikaatiot/kun-ilmoitus-lahetetty
        tapahtumat ilmoitus-id
        (fn [valitystapa]
          (let [kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (time/now) "valitetty" urakka paivystaja nil)
                lisatietoja (str "Välitystapa: " valitystapa)]
            (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id true lisatietoja)))))))

(defn kasittele-tuntematon-urakka [sonja lokittaja kuittausjono viesti-id ilmoitus-id korrelaatio-id tapahtuma-id]
  (let [virhe (format "Urakkaa ei voitu päätellä T-LOIK:n ilmoitukselle (id: %s, viesti id: %s)" ilmoitus-id viesti-id)
        kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (.toString (time/now)) "virhe" nil nil "Tiedoilla ei voitu päätellä urakkaa.")]
    (log/error virhe)
    (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id false virhe)))

(defn vastaanota-ilmoitus [sonja lokittaja sms tapahtumat db kuittausjono viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [jms-viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        korrelaatio-id (.getJMSCorrelationID viesti)
        tapahtuma-id (lokittaja :saapunut-jms-viesti jms-viesti-id viestin-sisalto)
        {:keys [viesti-id ilmoitus-id] :as ilmoitus} (lue-ilmoitus sonja lokittaja kuittausjono korrelaatio-id tapahtuma-id viesti)]
    (try+
      (let [urakka (hae-urakka db ilmoitus)]
        (if urakka
          (kasittele-ilmoitus sms db tapahtumat urakka ilmoitus)
          (kasittele-tuntematon-urakka sonja lokittaja kuittausjono viesti-id ilmoitus-id korrelaatio-id tapahtuma-id)))
      (catch Exception e
        (log/error e (format "Tapahtui poikkeus luettaessa sisään ilmoitusta T-LOIK:sta (id: %s, viesti id: %s)" ilmoitus-id viesti-id))
        (let [virhe (str (format "Poikkeus (id: %s, viesti id: %s) " ilmoitus-id viesti-id) e)
              kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (.toString (time/now)) "virhe" nil nil "Sisäinen käsittelyvirhe.")]
          (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id false virhe))))))
