(ns harja.palvelin.integraatiot.sampo.kasittely.organisaatiot
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.organisaatiot :as organisaatiot]
            [harja.kyselyt.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.tyokalut.merkkijono :as merkkijono])
  (:use [slingshot.slingshot :only [throw+]]))

(defn paivita-organisaatio [db organisaatio-id nimi y-tunnus katuosoite postinumero]
  (log/debug "Päivitetään organisaatio, jonka id on: " organisaatio-id ".")
  (organisaatiot/paivita-organisaatio! db nimi y-tunnus katuosoite postinumero organisaatio-id))

(defn luo-organisaatio [db sampo-id nimi y-tunnus katuosoite postinumero]
  (log/debug "Luodaan uusi organisaatio.")
  (let [uusi-id (:id (organisaatiot/luo-organisaatio<! db sampo-id nimi y-tunnus katuosoite postinumero))]
    (log/debug "Uusi organisaatio id on:" uusi-id)
    uusi-id))

(defn tallenna-organisaatio [db sampo-id nimi y-tunnus katuosoite postinumero]
  (let [organisaatio-id (:id (first (organisaatiot/hae-id-sampoidlla db sampo-id)))
        postinumero (merkkijono/leikkaa 5 postinumero)
        y-tunnus (merkkijono/leikkaa 9 y-tunnus)]
    (if organisaatio-id
      (do
        (paivita-organisaatio db organisaatio-id nimi y-tunnus katuosoite postinumero)
        organisaatio-id)
      (do
        (luo-organisaatio db sampo-id nimi y-tunnus katuosoite postinumero)))))

(defn kasittele-organisaatio [db {:keys [viesti-id sampo-id nimi y-tunnus katuosoite postinumero]}]
  (log/debug "Käsitellään organisaatio Sampo id:llä: " sampo-id)

  (try
    (let [organisaatio-id (tallenna-organisaatio db sampo-id nimi y-tunnus katuosoite postinumero)]
      (log/debug "Käsiteltävän organisaation id on:" organisaatio-id)
      (urakat/aseta-urakoitsija-urakoille-yhteyshenkilon-kautta! db sampo-id)

      (log/debug "Organisaatio käsitelty onnistuneesti")
      (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Company"))

    (catch Exception e
      (log/error e "Tapahtui poikkeus tuotaessa organisaatiota Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Company" "Internal Error")]
        (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet  [{:poikkeus e}]})))))

(defn kasittele-organisaatiot [db organisaatiot]
  (mapv #(kasittele-organisaatio db %) organisaatiot))
