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
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn laheta-kuittaus [sonja integraatioloki kuittausjono kuittaus tapahtuma-id lisatietoja]
  (log/debug "Lähetetään kuittaus Sampon jonoon:" kuittaus)
  (sonja/laheta sonja kuittausjono kuittaus)
  (integraatioloki/kirjaa-lahteva-jms-kuittaus
    integraatioloki
    kuittaus
    tapahtuma-id
    (kuittaus-sampoon-sanoma/onko-kuittaus-positiivinen? kuittaus)
    lisatietoja))

(defn kasittele-viesti [sonja integraatioloki db kuittausjono viesti]
  (log/debug "Vastaanotettiin Sampon viestijonosta viesti:" viesti)
  (let [viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        tapahtuma-id (integraatioloki/kirjaa-saapunut-jms-viesti integraatioloki "sampo" "sisaanluku" viesti-id viestin-sisalto)]
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
      (catch [:type virheet/+poikkeus-samposisaanluvussa+] {:keys [virheet kuittaus]}
        (laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id (str virheet)))
      (catch Exception e
        (integraatioloki/kirjaa-epaonnistunut-integraatio
          integraatioloki
          "Sampo sisäänluvussa tapahtui poikkeus"
          (str "Poikkeus: " (.getMessage e))
          tapahtuma-id
          nil)
        (log/error "Sampo sisäänluvussa tapahtui poikkeus." e)))))