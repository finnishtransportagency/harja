(ns harja.palvelin.integraatiot.sampo.kasittely.sopimukset
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.kyselyt.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn paivita-sopimus [db sopimus-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id]
  (log/debug "Päivitetään sopimus, jonka id on: " sopimus-id ".")
  (sopimukset/paivita-sopimus! db nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id sopimus-id))

(defn luo-sopimus [db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id]
  (log/debug "Luodaan uusi sopimus.")
  (let [uusi-id (:id (sopimukset/luo-sopimus<! db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id))]
    (log/debug "Uusi sopimus id on:" uusi-id)
    uusi-id))

(defn tallenna-sopimus [db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id]
  (let [sopimus-id (:id (first (sopimukset/hae-id-sampoidlla db sampo-id)))]
    (if sopimus-id
      (do
        (paivita-sopimus db sopimus-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id)
        sopimus-id)
      (do
        (luo-sopimus db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id)))))

(defn kasittele-sopimus [db {:keys [viesti-id sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id]}]
  (log/debug "Käsitellään sopimus sampo id:llä: " sampo-id)

  (try
    (let [sopimus-id (tallenna-sopimus db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id)]

      (log/debug "Käsiteltävän sopimukset id on:" sopimus-id)
      (sopimukset/paivita-urakka-sampoidlla! db urakka-sampo-id)
      (urakat/aseta-urakoitsija-sopimuksen-kautta! db sampo-id)

      (log/debug "Organisaatio käsitelty onnistuneesti")
      (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Order"))

    (catch Exception e
      (log/error e "Tapahtui poikkeus tuotaessa sopimusta Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Order" "Internal Error")]
        (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet  [{:poikkeus e}]})))))

(defn kasittele-sopimukset [db sopimukset]
  (mapv #(kasittele-sopimus db %) sopimukset))
