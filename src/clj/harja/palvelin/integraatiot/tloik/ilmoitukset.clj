(ns harja.palvelin.integraatiot.tloik.ilmoitukset
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tloik.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn vastaanota-ilmoitus [sonja integraatioloki db kuittausjono viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        tapahtuma-id (integraatioloki/kirjaa-jms-viesti integraatioloki "tloik" "ilmoituksen-kirjaus" viesti-id "sisään" viestin-sisalto)]
    (try+
      (jdbc/with-db-transaction [transaktio db]
        #_(let [data (sampo-sanoma/lue-viesti viestin-sisalto)
              hankkeet (:hankkeet data)
              urakat (:urakat data)
              sopimukset (:sopimukset data)
              toimenpiteet (:toimenpideinstanssit data)
              organisaatiot (:organisaatiot data)
              yhteyshenkilot (:yhteyshenkilot data)
              kuittaukset (concat (hankkeet/kasittele-hankkeet transaktio hankkeet)
                                  (urakat/kasittele-urakat transaktio urakat)
                                  (sopimukset/kasittele-sopimukset transaktio sopimukset)
                                  (toimenpiteet/kasittele-toimenpiteet transaktio toimenpiteet)
                                  (organisaatiot/kasittele-organisaatiot transaktio organisaatiot)
                                  (yhteyshenkilot/kasittele-yhteyshenkilot transaktio yhteyshenkilot))]
          (doseq [kuittaus kuittaukset]
            (laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id nil))))
      (catch [:type virheet/+poikkeus-ilmoitusten-vastaanotossa+] {:keys [virheet kuittaus]}
        #_(laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id (str virheet)))
      (catch Exception e
        (log/error e "Tapahtui poikkeus luettaessa sisään ilmoitusta T-LOIK:sta.")))))



