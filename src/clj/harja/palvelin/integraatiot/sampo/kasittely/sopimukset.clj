(ns harja.palvelin.integraatiot.sampo.kasittely.sopimukset
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.kyselyt.urakat :as urakat]))

(defn hae-paasopimuksen-id [db urakka-sampo-id]
  (:id (first (sopimukset/hae-paasopimuksen-id-urakan-sampoidlla db urakka-sampo-id ))))

(defn paivita-sopimus [db sopimus-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id]
  (log/debug "Päivitetään sopimus, jonka id on: " sopimus-id ".")
  (sopimukset/paivita-sopimus! db nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id sopimus-id))

(defn luo-sopimus [db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id paasopimus-id]
  (log/debug "Luodaan uusi sopimus. Paasopimuksen id:" paasopimus-id)
  (let [uusi-id (:id (sopimukset/luo-sopimus<! db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id paasopimus-id))]
    (log/debug "Uusi sopimus id on:" uusi-id)
    uusi-id))

(defn tallenna-sopimus [db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id paasopimus-id]
  (let [sopimus-id (:id (first (sopimukset/hae-id-sampoidlla db sampo-id)))]
    (if sopimus-id
      (do
        (paivita-sopimus db sopimus-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id)
        sopimus-id)
      (do
        (luo-sopimus db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id paasopimus-id)))))

(defn kasittele-sopimus [db {:keys [viesti-id sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id]}]
  (log/debug "Tallennetaan uusi sopimus sampo id:llä: " sampo-id)
  (let [paasopimus-id (hae-paasopimuksen-id db urakka-sampo-id)
        sopimus-id (tallenna-sopimus db sampo-id nimi alkupvm loppupvm urakka-sampo-id urakoitsija-sampo-id paasopimus-id)]
    (log/debug "Käsiteltävän sopimukset id on:" sopimus-id)
    (sopimukset/paivita-urakka-sampoidlla! db urakka-sampo-id)
    (urakat/aseta-urakoitsija-sopimuksen-kautta! db sampo-id)))

(defn kasittele-sopimukset [db sopimukset]
  (doseq [sopimus sopimukset]
    (kasittele-sopimus db sopimus)))
