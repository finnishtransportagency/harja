(ns harja.palvelin.integraatiot.sampo.urakat
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]))

(defn tallenna-urakka [db sampo-id nimi alkupvm loppupvm hanke-sampo-id]
  (let [urakka-id (:id (first (urakat/hae-id-sampoidlla db sampo-id)))]
    (log/debug "Urakka id: " urakka-id)
    (if urakka-id
      (do
        (log/debug "Paivitetaan urakka.")
        (urakat/paivita-urakka-samposta! db nimi alkupvm loppupvm hanke-sampo-id sampo-id)
        urakka-id)
      (do
        (log/debug "Lisataan uusi urakka.")
        (let [uusi-id (:id (urakat/luo-urakka<! db nimi alkupvm loppupvm hanke-sampo-id sampo-id))]
          (log/debug "Uusi urakka id on:" uusi-id)
          uusi-id)))))

(defn kasittele-urakka [db {:keys [sampo-id nimi alkupvm loppupvm hanke-sampo-id yhteyshenkilo-sampo-id]}]
  (log/debug "Tallennetaan uusi urakka sampo id:llä: " sampo-id)
  (let [urakka-id (tallenna-urakka db sampo-id nimi alkupvm loppupvm hanke-sampo-id)]
    (log/debug "Käsiteltävän urakan id on:" urakka-id)
    (urakat/paivita-hankkeen-tiedot-urakalle! db hanke-sampo-id)
    (yhteyshenkilot/liita-sampon-yhteyshenkilo-urakkaan<! db yhteyshenkilo-sampo-id urakka-id)
    ;; todo: paivita sopimukset & toimenpideinstanssit
    ))

(defn kasittele-urakat [db urakat]
  (doseq [urakka urakat]
    (kasittele-urakka db urakka)))