(ns harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitustoimenpide-sanoma :as toimenpide-sanoma]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (java.util UUID)))

(defn laheta [jms-lahettaja db id]
  (let [viesti-id (str (UUID/randomUUID))
        data (konversio/alaviiva->rakenne (first (ilmoitukset/hae-ilmoitustoimenpide db id)))
        xml (toimenpide-sanoma/muodosta data viesti-id)]
    (if xml
      (do
        (jms-lahettaja xml viesti-id)
        (ilmoitukset/merkitse-ilmoitustoimenpide-odottamaan-vastausta! db viesti-id id)
        (when (= "lopetus" (:kuittaustyyppi data))
          (ilmoitukset/merkitse-ilmoitustoimenpide-suljetuksi! db (:ilmoitus data)))
        (log/debug (format "Ilmoitustoimenpiteen (id: %s) lähetys T-LOIK:n onnistui." id)))
      (do
        (log/error (format "Ilmoitustoimenpiteen (id: %s) lähetys T-LOIK:n epäonnistui." id))
        (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db id)))))

(defn laheta-ilmoitustoimenpide [jms-lahettaja db id]
  (log/debug (format "Lähetetään ilmoitustoimenpide (id: %s) T-LOIK:n." id))
  (try
    (lukko/aja-lukon-kanssa db "tloik-ilm.toimenpidelahetys" (fn [] (laheta jms-lahettaja db id)))
    (catch Exception e
      (log/error e (format "Ilmoitustoimenpiteen (id: %s) lähetyksessä T-LOIK:n tapahtui poikkeus." id))
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db id)
      (throw e))))

(defn vastaanota-kuittaus [db viesti-id onnistunut]
  (if onnistunut
    (do
      (log/debug (format "Ilmoitustoimenpide kuitattiin T-LOIK:sta onnistuneeksi viesti-id:llä: %s" viesti-id))
      (ilmoitukset/merkitse-ilmoitustoimenpide-lahetetyksi! db viesti-id))

    (do
      (log/error (format "Ilmoitustoimenpide kuitattiin T-LOIK:sta epäonnistuneeksi viesti-id:llä: %s" viesti-id))
      (ilmoitukset/merkitse-ilmoitustoimenpidelle-lahetysvirhe! db viesti-id))))

(defn vastaanota-sahkopostikuittaus [db viesti]
  ;; PENDING: viestien käsittely toteutettava,
  ;; ks. otsikosta esim. pattern #ur/ilm, jossa urakan ja ilmoituksen id
  ;; bodysta haetaan onko kyseessä minkä tyyppinen kuittaus

  (log/debug (format "Vastaanotettiin T-LOIK kuittaus sähköpostilla. Viesti: %s." viesti))
  nil)

(defn hae-paivystaja [db puhelinnumero]
  ;; todo: tämä on naivi toteutus. jatkossa pitää käyttää jarin tekemää puhelinnumeron parsintaa, jotta tuetaan useita eri puhelinnumeron muotoja.
  (if-let [paivystaja (yhteyshenkilot/hae-paivystaja-puhelinnumerolla db puhelinnumero)]
    paivystaja
    (throw+ {:type :tuntematon-kayttaja})))

(defn parsi-toimenpide [toimenpide]
  (case toimenpide
    "V" :vastaanotto
    "A" :aloitus
    "L" :lopetus
    (throw+ {:type    :tuntematon-ilmoitustoimenpide
             :virheet [{:koodi  :tuntematon-ilmoitustoimenpide
                        :viesti (format "Tuntematon ilmoitustoimenpide: %s" toimenpide)}]})))

(defn parsi-viestinumero [numero]
  (if (merkkijono/onko-kokonaisluku? numero)
    (Integer/parseInt numero)
    (throw+ {:type    :tuntematon-viestinumero
             :virheet [{:koodi  :tuntematon-viestinumero
                        :viesti (format "Tuntematon viestinumero: %s" numero)}]})))

(defn parsi-tekstiviesti [viesti]
  {:toimenpide   (parsi-toimenpide (str (nth viesti 0)))
   :viestinumero (parsi-viestinumero (str (nth viesti 1)))})

(defn hae-ilmoitus-id [db viestinumero paivystaja]
  (if-let [id (paivystajatekstiviestit/hae-ilmoitus db viestinumero (:id paivystaja))]
    id
    (throw+ {:type :tuntematon-ilmoitus})))

(defn tallenna-ilmoitustoimenpide [db ilmoitus-id toimenpide paivystaja]
  )

(defn vastaanota-tekstiviestikuittaus [jms-lahettaja sms db puhelinnumero viesti]
  (log/debug (format "Vastaanotettiin T-LOIK kuittaus tekstiviestillä. Numero: %s, viesti: %s." puhelinnumero viesti))

  (try+
    (let [paivystaja (hae-paivystaja db puhelinnumero)
          data (parsi-tekstiviesti viesti)
          ilmoitus-id (hae-ilmoitus-id db (:viestinumero data) paivystaja)
          ilmoitustoimenpide-id (tallenna-ilmoitustoimenpide db ilmoitus-id (:toimepide data) paivystaja)]

      (laheta-ilmoitustoimenpide jms-lahettaja db ilmoitustoimenpide-id)
      (sms/laheta sms puhelinnumero "Viestisi käsiteltiin onnistuneesti. Kiitos!"))

    (catch [:type :tuntematon-kayttaja] {}
      (log/error (format "Numerosta: %s vastaanotettua viestiä: %s ei voida käsitellä, sillä puhelinnumerolla ei löydy käyttäjää." puhelinnumero viesti))
      (sms/laheta sms puhelinnumero "Viestiä ei voida käsitellä, sillä käyttäjää ei voitu tunnistaa puhelinnumerolla."))

    (catch [:type :tuntematon-ilmoitustoimenpide] {}
      (log/error (format "Numerosta: %s vastaanotetussa viestissä: %s toimenpide ei ole validi." puhelinnumero viesti))
      (sms/laheta sms puhelinnumero "Viestiäsi ei voitu käsitellä. Antamasi toimenpide ei ole validi. Vastaa viestiin toimenpiteen lyhenteellä ja viestinumerolla."))

    (catch [:type :tuntematon-viestinumero] {}
      (log/error (format "Numerosta: %s vastaanotetussa viestissä: %s viestinumero ei ole validi." puhelinnumero viesti))
      (sms/laheta sms puhelinnumero "Viestiäsi ei voitu käsitellä. Antamasi viestinumero ei ole validi. Vastaa viestiin toimenpiteen lyhenteellä ja viestinumerolla."))

    (catch [:type :tuntematon-ilmoitus] {}
      (log/error (format "Numerosta: %s vastaanotetulla viestillä: %s ei löydetty ilmoitusta." puhelinnumero viesti))
      (sms/laheta sms puhelinnumero "Viestiäsi ei voitu käsitellä. Antamallasi viestinumerolla ei löydy ilmoitusta. Vastaa viestiin toimenpiteen lyhenteellä ja viestinumerolla."))

    (catch Exception e
      (log/error e (format "Numerosta: %s vastaanotetun viestin: %s käsittelyssä tapahtui poikkeus." puhelinnumero viesti))
      (sms/laheta sms puhelinnumero "Pahoittelemme mutta lähettämäsi viestin käsittelyssä tapahtui virhe."))))


