(ns harja.palvelin.integraatiot.sampo.tuonti
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.sanomat.sampo-sanoma :as sampo-sanoma]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sampoon-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.hankkeet :as hankkeet]
            [harja.palvelin.integraatiot.sampo.kasittely.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.kasittely.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet :as toimenpiteet]
            [harja.palvelin.integraatiot.sampo.kasittely.organisaatiot :as organisaatiot]
            [harja.palvelin.integraatiot.sampo.kasittely.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.sampo.tyokalut.lokitus :as sampo-lokitus])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn laheta-kuittaus [sonja integraatioloki kuittausjono kuittaus tapahtuma-id lisatietoja]
  (sampo-lokitus/lokita-lahteva-kuittaus integraatioloki kuittaus tapahtuma-id (kuittaus-sampoon-sanoma/onko-kuittaus-positiivinen? kuittaus) lisatietoja)
  (sonja/laheta sonja kuittausjono kuittaus))

(defn kasittele-viesti [sonja integraatioloki db kuittausjono viesti]
  (log/debug "Vastaanotettiin Sampon viestijonosta viesti: " viesti)
  (let [viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        tapahtuma-id (sampo-lokitus/lokita-viesti integraatioloki "sisaanluku" viesti-id "sisään" viestin-sisalto)]
    (try+
      (jdbc/with-db-transaction [transaktio db]
        (let [data (sampo-sanoma/lue-viesti viestin-sisalto)
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
      (catch [:type sampo-lokitus/+poikkeus-samposisaanluvussa+] {:keys [virheet kuittaus]}
        (laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id (str virheet)))
      (catch Exception e
        (log/error e "Tapahtui poikkeus luettaessa sisään viestiä Samposta.")))))