(ns harja.palvelin.integraatiot.sampo.tuonti
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.sanomat.sampo-sanoma :as sampo-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.hankkeet :as hankkeet]
            [harja.palvelin.integraatiot.sampo.kasittely.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.kasittely.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet :as toimenpiteet]
            [harja.palvelin.integraatiot.sampo.kasittely.organisaatiot :as organisaatiot]
            [harja.palvelin.integraatiot.sampo.kasittely.yhteyshenkilot :as yhteyshenkilot])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn laheta-kuittaus [sonja kuittausjono kuittaus]
  (sonja/laheta sonja kuittausjono kuittaus))

(defn kasittele-viesti [sonja db kuittausjono viesti]
  (log/debug "Vastaanotettiin Sampon viestijonosta viesti: " viesti)
  (try+
    (jdbc/with-db-transaction [transaktio db]
      (let [data (sampo-sanoma/lue-viesti (.getText viesti))
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
          (laheta-kuittaus sonja kuittausjono kuittaus))))

    (catch Exception e
      (log/error e "Tapahtui poikkeus luettaessa sisään viestiä Samposta."))))