(ns harja.palvelin.integraatiot.sampo.kasittely.yhteyshenkilot
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]))

(defn paivita-yhteyshenkilo [db yhteyshenkilo-id etunimi sukunimi kayttajatunnus sahkoposti]
  (log/debug "Päivitetään yhteyshenkilo, jonka id on: " yhteyshenkilo-id ".")
  (yhteyshenkilot/paivita-yhteyshenkilo! db etunimi sukunimi nil nil sahkoposti nil yhteyshenkilo-id))

(defn luo-yhteyshenkilo [db sampo-id etunimi sukunimi kayttajatunnus sahkoposti]
  (log/debug "Luodaan uusi yhteyshenkilo.")
  (let [uusi-id (:id (yhteyshenkilot/luo-yhteyshenkilo<! db etunimi sukunimi nil nil sahkoposti nil sampo-id kayttajatunnus))]
    (log/debug "Uusi yhteyshenkilo id on:" uusi-id)
    uusi-id))

(defn tallenna-yhteyshenkilo [db sampo-id etunimi sukunimi kayttajatunnus sahkoposti]
  (let [yhteyshenkilo-id (:id (first (yhteyshenkilot/hae-id-sampoidlla db sampo-id)))]
    (if yhteyshenkilo-id
      (do
        (paivita-yhteyshenkilo db yhteyshenkilo-id etunimi sukunimi kayttajatunnus sahkoposti)
        yhteyshenkilo-id)
      (do
        (luo-yhteyshenkilo db sampo-id etunimi sukunimi kayttajatunnus sahkoposti)))))

(defn kasittele-yhteyshenkilo [db {:keys [viesti-id sampo-id etunimi sukunimi kayttajatunnus sahkoposti]}]
  (log/debug "Käsitellään yhteyshenkilo Sampo id:llä: " sampo-id)
  (let [yhteyshenkilo-id (tallenna-yhteyshenkilo db sampo-id etunimi sukunimi kayttajatunnus sahkoposti)]
    (log/debug "Käsiteltävän yhteyshenkilon id on:" yhteyshenkilo-id)
    (yhteyshenkilot/paivita-yhteyshenkilot-urakalle-sampoidlla! db sampo-id)))

(defn kasittele-yhteyshenkilot [db yhteyshenkilot]
  (doseq [yhteyshenkilo yhteyshenkilot]
    (kasittele-yhteyshenkilo db yhteyshenkilo)))