(ns harja.palvelin.integraatiot.tloik.kasittely.ilmoitus
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]))

(defn paattele-urakka [db urakkatyyppi sijainti]
  (let [urakka-id (:id (first (ilmoitukset/hae-ilmoituksen-urakka db urakkatyyppi (:x sijainti) (:y sijainti))))]
    (log/debug "-----Urakka id:" urakka-id "-----")
    (if (and (not urakka-id) (not (= "hoito" urakkatyyppi)))
      (:id (first (ilmoitukset/hae-ilmoituksen-urakka db "hoito" (:x sijainti) (:y sijainti))))
      urakka-id)))

(defn paivita-ilmoitus [db id {:keys [ilmoitettu ilmoitus-id ilmoitustyyppi valitettu urakkatyyppi vapaateksti yhteydenottopyynto ilmoittaja lahettaja selitteet sijainti vastaanottaja]}]
  ;; todo tallenna välitystiedot ja vastaanottaja, jos on jo välitetty
  (let [urakka (paattele-urakka db urakkatyyppi sijainti)]
    (ilmoitukset/paivita-ilmoitus!
      db
      urakka
      ilmoitus-id
      ilmoitettu
      valitettu
      yhteydenottopyynto
      vapaateksti
      ilmoitustyyppi
      (str "{" (clojure.string/join "," (map name selitteet)) "}")
      (:etunimi ilmoittaja)
      (:sukunimi ilmoittaja)
      (:tyopuhelin ilmoittaja)
      (:matkapuhelin ilmoittaja)
      (:sahkoposti ilmoittaja)
      (:tyyppi ilmoittaja)
      (:etunimi lahettaja)
      (:sukunimi lahettaja)
      (:puhelinnumero lahettaja)
      (:sahkoposti lahettaja)
      id)
    (ilmoitukset/aseta-ilmoituksen-sijainti! db (:x sijainti) (:y sijainti) (:tienumero sijainti) id)))

(defn luo-ilmoitus [db {:keys [ilmoitettu ilmoitus-id ilmoitustyyppi valitettu urakkatyyppi vapaateksti yhteydenottopyynto ilmoittaja lahettaja selitteet sijainti vastaanottaja]}]
  ;; todo tallenna vastaanottaja, jos on jo välitetty
  (let [urakka (paattele-urakka db urakkatyyppi sijainti)
        id (:id (ilmoitukset/luo-ilmoitus<!
                  db
                  urakka
                  ilmoitus-id
                  ilmoitettu
                  valitettu
                  yhteydenottopyynto
                  vapaateksti
                  ilmoitustyyppi
                  (str "{" (clojure.string/join "," (map name selitteet)) "}")
                  urakkatyyppi
                  (:etunimi ilmoittaja)
                  (:sukunimi ilmoittaja)
                  (:tyopuhelin ilmoittaja)
                  (:matkapuhelin ilmoittaja)
                  (:sahkoposti ilmoittaja)
                  (:tyyppi ilmoittaja)
                  (:etunimi lahettaja)
                  (:sukunimi lahettaja)
                  (:puhelinnumero lahettaja)
                  (:sahkoposti lahettaja)))]
    (ilmoitukset/aseta-ilmoituksen-sijainti! db (:x sijainti) (:y sijainti) (:tienumero sijainti) id)))

(defn kasittele-ilmoitus [db ilmoitus]
  (log/debug "Käsitellään ilmoitusta T-LOIK:sta id:llä: " (:ilmoitus-id ilmoitus) ", joka välitettiin viestillä id: " (:viesti-id ilmoitus))
  (let [id (:id (first (ilmoitukset/hae-id-ilmoitus-idlla db (:ilmoitus-id ilmoitus))))]
    (if id
      (paivita-ilmoitus db id ilmoitus)
      (luo-ilmoitus db ilmoitus)))
  (log/debug "Ilmoitus käsitelty onnistuneesti")
  #_(kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Program"))



