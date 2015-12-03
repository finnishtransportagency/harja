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
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+]]))

(defn laheta-kuittaus [sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id onnistunut lisatietoja]
  (lokittaja :lahteva-jms-kuittaus kuittaus tapahtuma-id onnistunut lisatietoja)
  (sonja/laheta sonja kuittausjono kuittaus {:correlation-id korrelaatio-id}))

(defn vastaanota-ilmoitus [sonja lokittaja tapahtumat db kuittausjono viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [jms-viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        korrelaatio-id (.getJMSCorrelationID viesti)
        tapahtuma-id (lokittaja :saapunut-jms-viesti jms-viesti-id viestin-sisalto)
        {:keys [viesti-id ilmoitus-id] :as ilmoitus} (ilmoitus-sanoma/lue-viesti viestin-sisalto)]
    (try+
      (let [
            urakka-id (jdbc/with-db-transaction [transaktio db]
                        (ilmoitus/kasittele-ilmoitus transaktio ilmoitus))
            urakka (first (urakat/hae-urakka db urakka-id))]
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
          (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id false virhe))))))
