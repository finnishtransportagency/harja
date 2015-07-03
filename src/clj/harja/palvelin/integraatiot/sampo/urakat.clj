(ns harja.palvelin.integraatiot.sampo.urakat
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]))

(defn kasittele-urakka [db {:keys [sampo-id nimi alkupvm loppupvm hanke-sampo-id yhteyshenkilo-sampo-id]}]
  (log/debug "Tallennetaan uusi urakka sampo id:llä: " sampo-id)
  (let [urakka-id
        (if (urakat/onko-tuotu-samposta? db sampo-id)
          (urakat/paivita-urakka-samposta! db nimi alkupvm loppupvm hanke-sampo-id sampo-id )
          (urakat/luo-urakka<! db nimi alkupvm loppupvm hanke-sampo-id sampo-id))]
    (urakat/paivita-hankkeen-tiedot-urakalle! db hanke-sampo-id)
    ;(yhteyshenkilot/liita-yhteyshenkilo-urakkaan-sampoidlla<! yhteyshenkilo-sampo-id urakka-id)
    ;; todo: paivita sopimukset & toimenpideinstanssit
    ))

(defn kasittele-urakat [db urakat]
  (doseq [urakka urakat]
    (kasittele-urakka db urakka)))