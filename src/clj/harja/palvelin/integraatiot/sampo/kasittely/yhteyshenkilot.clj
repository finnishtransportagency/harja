(ns harja.palvelin.integraatiot.sampo.kasittely.yhteyshenkilot
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn paivita-yhteyshenkilo [db yhteyshenkilo-id etunimi sukunimi sahkoposti]
  (log/debug "Päivitetään yhteyshenkilo, jonka id on: " yhteyshenkilo-id ".")
  (yhteyshenkilot/paivita-yhteyshenkilo db etunimi sukunimi nil nil sahkoposti nil yhteyshenkilo-id))

(defn luo-yhteyshenkilo [db sampo-id etunimi sukunimi kayttajatunnus sahkoposti]
  (log/debug "Luodaan uusi yhteyshenkilo.")
  (let [uusi-id (:id (yhteyshenkilot/luo-yhteyshenkilo db etunimi sukunimi nil nil sahkoposti nil sampo-id kayttajatunnus nil))]
    (log/debug "Uusi yhteyshenkilo id on:" uusi-id)
    uusi-id))

(defn tallenna-yhteyshenkilo [db sampo-id etunimi sukunimi sahkoposti]
  (let [yhteyshenkilo-id (:id (first (yhteyshenkilot/hae-id-sampoidlla db sampo-id)))]
    (if yhteyshenkilo-id
      (do
        (paivita-yhteyshenkilo db yhteyshenkilo-id etunimi sukunimi sahkoposti)
        yhteyshenkilo-id)
      (do
        (luo-yhteyshenkilo db sampo-id etunimi sukunimi sampo-id sahkoposti)))))

(defn kasittele-yhteyshenkilo [db {:keys [viesti-id sampo-id etunimi sukunimi sahkoposti]}]
  (log/debug "Käsitellään yhteyshenkilo Sampo id:llä: " sampo-id)

  (try
    (let [yhteyshenkilo-id (tallenna-yhteyshenkilo db sampo-id etunimi sukunimi sahkoposti)]
      (log/debug "Käsiteltävän yhteyshenkilon id on:" yhteyshenkilo-id)
      (yhteyshenkilot/paivita-yhteyshenkilot-urakalle-sampoidlla! db sampo-id)

      (log/debug "Yhteyshenkilo käsitelty onnistuneesti")
      (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Resource"))

    (catch Exception e
      (log/error e "Tapahtui poikkeus tuotaessa yhteyshenkilo Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Resource" "Internal Error")]
        (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet  [{:poikkeus e}]})))))

(defn kasittele-yhteyshenkilot [db yhteyshenkilot]
  (mapv #(kasittele-yhteyshenkilo db %) yhteyshenkilot))
