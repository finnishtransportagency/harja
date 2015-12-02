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
            [harja.kyselyt.urakat :as urakat]))

(defn laheta-kuittaus [sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id onnistunut lisatietoja]
  (lokittaja :lahteva-jms-kuittaus kuittaus tapahtuma-id onnistunut lisatietoja)
  (sonja/laheta sonja kuittausjono kuittaus {:correlation-id korrelaatio-id}))

(defn hae-urakoitsija [db urakka-id]
  (first (urakat/hae-urakan-organisaatio db urakka-id)))

(defn vastaanota-ilmoitus [sonja lokittaja tapahtumat db kuittausjono viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        korrelaatio-id (.getJMSCorrelationID viesti)
        tloik-viesti-id (:viesti-id viestin-sisalto)
        tapahtuma-id (lokittaja :saapunut-jms-viesti viesti-id viestin-sisalto)]

    (try
      (let [{:keys [viesti-id ilmoitus-id] :as ilmoitus} (ilmoitus-sanoma/lue-viesti viestin-sisalto)
            urakka-id (jdbc/with-db-transaction [transaktio db]
                        (ilmoitus/kasittele-ilmoitus transaktio tapahtumat ilmoitus))
            urakoitsija (hae-urakoitsija db urakka-id)]
          
        (notifikaatiot/ilmoita-saapuneesta-ilmoituksesta tapahtumat urakka-id ilmoitus-id)
        ;; Odotetaan, että ilmoitus on lähetetty urakoitsijalle
        (notifikaatiot/kun-ilmoitus-lahetetty
         tapahtumat ilmoitus-id
         (fn [valitystapa]
           ;; Lähetä T-LOIK kuittaus kun ilmoitus on lähetetty urakoitsijalle
           (laheta-kuittaus sonja lokittaja kuittausjono
                            (kuittaus/muodosta (:viesti-id ilmoitus) (time/now) "valitetty" urakoitsija nil)
                            korrelaatio-id tapahtuma-id true
                            (str "välitystapa: " valitystapa)))))
        
      (catch Exception e
        (log/error e "Tapahtui poikkeus luettaessa sisään ilmoitusta T-LOIK:sta.")
        (let [virhe (str "Poikkeus: " e)
              kuittaus (kuittaus/muodosta tloik-viesti-id (.toString (time/now)) "virhe" nil virhe)]
            (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id false virhe))))))
