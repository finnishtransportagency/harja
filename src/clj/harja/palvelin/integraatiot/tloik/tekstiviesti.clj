(ns harja.palvelin.integraatiot.tloik.tekstiviesti
  "Ilmoitusten lähettäminen urakoitsijalle ja kuittausten vastaanottaminen"
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.domain.ilmoitukset :as apurit]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +ilmoitusviesti+
  (str "Uusi toimenpidepyyntö: %s (id: %s, viestinumero: %s).\n\n"
       "%s\n\n"
       "Selitteet: %s.\n\n"
       "Kuittauskoodit:\n"
       "V%s = vastaanotettu\n"
       "A%s = aloitettu\n"
       "L%s = lopetettu\n\n"
       "Vastaa lähettämällä kuittauskoodi sekä kommentti. Esim. A1 Työt aloitettu.\n"))

(def +onnistunut-viesti+ "Kuittaus käsiteltiin onnistuneesti. Kiitos!")
(def +viestinumero-tai-toimenpide-puuttuuviesti+ "Viestiä ei voida käsitellä. Kuittauskoodi puuttuu.")
(def +tuntematon-kayttaja-viesti+ "Viestiä ei voida käsitellä, sillä käyttäjää ei voitu tunnistaa puhelinnumerolla.")
(def +virheellinen-toimenpide-viesti+ "Viestiäsi ei voitu käsitellä. Antamasi kuittaus ei ole validi. Vastaa viestiin kuittauskoodilla ja kommentilla.")
(def +virheellinen-viestinumero-viesti+ "Viestiäsi ei voitu käsitellä. Antamasi viestinumero ei ole validi. Vastaa viestiin kuittauskoodilla ja kommentilla.")
(def +tuntematon-viestinumero-viesti+ "Viestiäsi ei voitu käsitellä. Antamallasi viestinumerolla ei löydy avointa ilmoitusta. Vastaa viestiin kuittauskoodilla ja kommentilla.")
(def +virhe-viesti+ "Pahoittelemme mutta lähettämäsi viestin käsittelyssä tapahtui virhe.")

(defn parsi-toimenpide [toimenpide]
  (case toimenpide
    "V" "vastaanotto"
    "A" "aloitus"
    "L" "lopetus"
    (throw+ {:type :tuntematon-ilmoitustoimenpide
             :virheet [{:koodi :tuntematon-ilmoitustoimenpide
                        :viesti (format "Tuntematon ilmoitustoimenpide: %s" toimenpide)}]})))

(defn parsi-viestinumero [numero]
  (if (merkkijono/onko-kokonaisluku? numero)
    (Integer/parseInt numero)
    (throw+ {:type :tuntematon-viestinumero
             :virheet [{:koodi :tuntematon-viestinumero
                        :viesti (format "Tuntematon viestinumero: %s" numero)}]})))

(defn parsi-vapaateksti [viesti]
  (when (< 2 (count viesti)) (string/trim (.substring viesti 2 (count viesti)))))

(defn parsi-tekstiviesti [viesti]
  (when (> 2 (count viesti))
    (throw+ {:type :viestinumero-tai-toimenpide-puuttuu}))
  (let [toimenpide (parsi-toimenpide (str (nth viesti 0)))
        viestinumero (parsi-viestinumero (str (nth viesti 1)))
        vapaateksti (parsi-vapaateksti viesti)]
    {:toimenpide toimenpide
     :viestinumero viestinumero
     :vapaateksti vapaateksti}))

(defn hae-paivystajatekstiviesti [db viestinumero puhelinnumero]
  (if-let [paivystajatekstiviesti (first (paivystajatekstiviestit/hae-puhelin-ja-viestinumerolla db puhelinnumero viestinumero))]
    paivystajatekstiviesti
    (throw+ {:type :tuntematon-ilmoitus})))

(defn vastaanota-tekstiviestikuittaus [jms-lahettaja db puhelinnumero viesti]
  (log/debug (format "Vastaanotettiin T-LOIK kuittaus tekstiviestillä. Numero: %s, viesti: %s." puhelinnumero viesti))
  (try+
    (let [data (parsi-tekstiviesti viesti)
          paivystajatekstiviesti (hae-paivystajatekstiviesti db (:viestinumero data) puhelinnumero)
          paivystaja (yhteyshenkilot/hae-yhteyshenkilo db (:yhteyshenkilo paivystajatekstiviesti))
          ilmoitustoimenpide-id (ilmoitustoimenpiteet/tallenna-ilmoitustoimenpide
                                  db
                                  (:ilmoitus paivystajatekstiviesti)
                                  (:ilmoitusid paivystajatekstiviesti)
                                  (:vapaateksti data)
                                  (:toimenpide data)
                                  paivystaja)]

      (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja db ilmoitustoimenpide-id)
      +onnistunut-viesti+)

    (catch [:type :viestinumero-tai-toimenpide-puuttuu] {}
      (log/error (format "Numerosta: %s vastaanotettua viestiä: %s ei voida käsitellä, toimenpide tai viestinumero puuttuu." puhelinnumero viesti))
      +viestinumero-tai-toimenpide-puuttuuviesti+)

    (catch [:type :tuntematon-kayttaja] {}
      (log/error (format "Numerosta: %s vastaanotettua viestiä: %s ei voida käsitellä, sillä puhelinnumerolla ei löydy käyttäjää." puhelinnumero viesti))
      +tuntematon-kayttaja-viesti+)

    (catch [:type :tuntematon-ilmoitustoimenpide] {}
      (log/error (format "Numerosta: %s vastaanotetussa viestissä: %s toimenpide ei ole validi." puhelinnumero viesti))
      +virheellinen-toimenpide-viesti+)

    (catch [:type :tuntematon-viestinumero] {}
      (log/error (format "Numerosta: %s vastaanotetussa viestissä: %s viestinumero ei ole validi." puhelinnumero viesti))
      +virheellinen-viestinumero-viesti+)

    (catch [:type :tuntematon-ilmoitus] {}
      (log/error (format "Numerosta: %s vastaanotetulla viestillä: %s ei löydetty ilmoitusta." puhelinnumero viesti))
      +tuntematon-viestinumero-viesti+)

    (catch Exception e
      (log/error e (format "Numerosta: %s vastaanotetun viestin: %s käsittelyssä tapahtui poikkeus." puhelinnumero viesti))
      +virhe-viesti+)))

(defn laheta-ilmoitus-tekstiviestilla [sms db ilmoitus paivystaja]
  (try
    (if-let [puhelinnumero (or (:matkapuhelin paivystaja) (:tyopuhelin paivystaja))]
      (do
        (log/debug (format "Lähetetään ilmoitus (id: %s) tekstiviestillä numeroon: %s" (:ilmoitus-id ilmoitus) puhelinnumero))
        (let [paivystaja-id (:id paivystaja)
              ilmoitus-id (:ilmoitus-id ilmoitus)
              otsikko (:otsikko ilmoitus)
              lyhytselite (:lyhytselite ilmoitus)
              selitteet (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))
              viestinumero (paivystajatekstiviestit/kirjaa-uusi-viesti db paivystaja-id ilmoitus-id puhelinnumero)
              viesti (format +ilmoitusviesti+
                             otsikko
                             ilmoitus-id
                             viestinumero
                             lyhytselite
                             selitteet
                             viestinumero
                             viestinumero
                             viestinumero
                             viestinumero)]
          (sms/laheta sms puhelinnumero viesti)))
      (log/warn "Ilmoitusta ei voida lähettää tekstiviestillä ilman puhelinnumeroa."))
    (catch Exception e
      (log/error "Ilmoituksen lähettämisessä tekstiviestillä tapahtui poikkeus." e))))

