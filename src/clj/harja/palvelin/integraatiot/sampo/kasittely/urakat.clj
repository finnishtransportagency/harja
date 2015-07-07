(ns harja.palvelin.integraatiot.sampo.kasittely.urakat
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]))

(defn paivita-urakka [db nimi alkupvm loppupvm hanke-sampo-id urakka-id]
  (log/debug "Päivitetään urakka, jonka id on: " urakka-id ".")
  (urakat/paivita-urakka! db nimi alkupvm loppupvm hanke-sampo-id urakka-id))

(defn luo-urakka [db nimi alkupvm loppupvm hanke-sampo-id sampo-id]
  (log/debug "Luodaan uusi urakka.")
  (let [uusi-id (:id (urakat/luo-urakka<! db nimi alkupvm loppupvm hanke-sampo-id sampo-id))]
    (log/debug "Uusi urakka id on:" uusi-id)
    uusi-id))

(defn tallenna-urakka [db sampo-id nimi alkupvm loppupvm hanke-sampo-id]
  (let [urakka-id (:id (first (urakat/hae-id-sampoidlla db sampo-id)))]
    (if urakka-id
      (do
        (paivita-urakka db nimi alkupvm loppupvm hanke-sampo-id urakka-id)
        urakka-id)
      (do
        (luo-urakka db nimi alkupvm loppupvm hanke-sampo-id sampo-id)))))

(defn paivita-yhteyshenkilo [db yhteyshenkilo-sampo-id urakka-id]
  (yhteyshenkilot/irrota-sampon-yhteyshenkilot-urakalta! db urakka-id)
  (yhteyshenkilot/liita-sampon-yhteyshenkilo-urakkaan<! db yhteyshenkilo-sampo-id urakka-id))

(defn paivita-sopimukset [db urakka-sampo-id]
  (sopimukset/paivita-urakka-sampoidlla! db urakka-sampo-id))

(defn paivita-toimenpiteet [db urakka-sampo-id]
  (toimenpiteet/paivita-urakka-sampoidlla! db urakka-sampo-id))

(defn kasittele-urakka [db {:keys [sampo-id nimi alkupvm loppupvm hanke-sampo-id yhteyshenkilo-sampo-id]}]
  (log/debug "Käsitellään urakka Sampo id:llä: " sampo-id)
  (let [urakka-id (tallenna-urakka db sampo-id nimi alkupvm loppupvm hanke-sampo-id)]
    (log/debug "Käsiteltävän urakan id on:" urakka-id)
    (urakat/paivita-hankkeen-tiedot-urakalle! db hanke-sampo-id)
    (paivita-yhteyshenkilo db yhteyshenkilo-sampo-id urakka-id)
    (paivita-sopimukset db sampo-id)
    (paivita-toimenpiteet db sampo-id)))

(defn kasittele-urakat [db urakat]
  (doseq [urakka urakat]
    (kasittele-urakka db urakka)))