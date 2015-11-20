(ns harja.palvelin.integraatiot.tloik.kasittely.ilmoitus
  (:require [taoensso.timbre :as log]
            [clj-time.core :as time]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]
            [harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma :as kuittaus]
            [harja.kyselyt.urakat :as urakat]))

(defn paattele-urakka [db urakkatyyppi sijainti]
  (let [urakka-id (:id (first (ilmoitukset/hae-ilmoituksen-urakka db urakkatyyppi (:x sijainti) (:y sijainti))))]
    (if (and (not urakka-id) (not (= "hoito" urakkatyyppi)))
      (:id (first (ilmoitukset/hae-ilmoituksen-urakka db "hoito" (:x sijainti) (:y sijainti))))
      urakka-id)))

(defn hae-urakoitsija [db urakka-id]
  (first (urakat/hae-urakan-organisaatio db urakka-id)))

(defn paivita-ilmoittaja [db id ilmoittaja]
  (ilmoitukset/paivita-ilmoittaja-ilmoitukselle!
    db
    (:etunimi ilmoittaja)
    (:sukunimi ilmoittaja)
    (:tyopuhelin ilmoittaja)
    (:matkapuhelin ilmoittaja)
    (:sahkoposti ilmoittaja)
    (:tyyppi ilmoittaja)
    id))

(defn paivita-lahettaja [db id lahettaja]
  (ilmoitukset/paivita-lahettaja-ilmoitukselle!
    db (:etunimi lahettaja)
    (:sukunimi lahettaja)
    (:puhelinnumero lahettaja)
    (:sahkoposti lahettaja)
    id))

(defn paivita-ilmoitus [db id urakka-id {:keys [ilmoitettu ilmoitus-id ilmoitustyyppi valitettu urakkatyyppi otsikko lyhytselite pitkaselite yhteydenottopyynto ilmoittaja lahettaja selitteet sijainti vastaanottaja]}]
  ;; todo tallenna välitystiedot ja vastaanottaja, jos on jo välitetty
  (ilmoitukset/paivita-ilmoitus!
    db
    urakka-id
    ilmoitus-id
    ilmoitettu
    valitettu
    yhteydenottopyynto
    otsikko
    lyhytselite
    pitkaselite
    ilmoitustyyppi
    (str "{" (clojure.string/join "," (map name selitteet)) "}")
    id)
  (paivita-ilmoittaja db id ilmoittaja)
  (paivita-lahettaja db id lahettaja)
  (ilmoitukset/aseta-ilmoituksen-sijainti! db (:tienumero sijainti) (:x sijainti) (:y sijainti) id))

(defn luo-ilmoitus [db urakka-id {:keys [ilmoitettu ilmoitus-id ilmoitustyyppi valitettu urakkatyyppi otsikko lyhytselite pitkaselite yhteydenottopyynto ilmoittaja lahettaja selitteet sijainti vastaanottaja]}]
  ;; todo tallenna vastaanottaja, jos on jo välitetty
  (let [id (:id (ilmoitukset/luo-ilmoitus<!
                  db
                  urakka-id
                  ilmoitus-id
                  ilmoitettu
                  valitettu
                  yhteydenottopyynto
                  otsikko
                  lyhytselite
                  pitkaselite
                  ilmoitustyyppi
                  (str "{" (clojure.string/join "," (map name selitteet)) "}")
                  urakkatyyppi))]
    (paivita-ilmoittaja db id ilmoittaja)
    (paivita-lahettaja db id lahettaja)
    (ilmoitukset/aseta-ilmoituksen-sijainti! db (:tienumero sijainti) (:x sijainti) (:y sijainti) id)))

(defn kasittele-ilmoitus [db ilmoitus]
  (log/debug "Käsitellään ilmoitusta T-LOIK:sta id:llä: " (:ilmoitus-id ilmoitus) ", joka välitettiin viestillä id: " (:viesti-id ilmoitus))
  (let [id (:id (first (ilmoitukset/hae-id-ilmoitus-idlla db (:ilmoitus-id ilmoitus))))
        urakka-id (paattele-urakka db (:urakkatyyppi ilmoitus) (:sijainti ilmoitus))
        urakoitsija (hae-urakoitsija db urakka-id)]
    (if id
      (paivita-ilmoitus db id urakka-id ilmoitus)
      (luo-ilmoitus db urakka-id ilmoitus))
    (log/debug "Ilmoitus käsitelty onnistuneesti")
    (kuittaus/muodosta (:viesti-id ilmoitus) (time/now) "valitetty" urakoitsija nil)))




