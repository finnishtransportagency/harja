(ns harja.palvelin.integraatiot.tloik.tekstiviesti
  "Ilmoitusten lähettäminen urakoitsijalle ja kuittausten vastaanottaminen"
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.domain.tieliikenneilmoitukset :as apurit]
            [harja.kyselyt.paivystajatekstiviestit :as paivystajatekstiviestit]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.fmt :as fmt]
            [harja.domain.tieliikenneilmoitukset :as ilm]
            [harja.kyselyt.tieliikenneilmoitukset :as ilmoitukset])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +ilmoitusviesti+
  (str "Uusi toimenpidepyyntö %s: %s (viestinumero: %s).\n\n"
       "Tunniste: %s\n\n"
       "Urakka: %s\n\n"
       "Yhteydenottopyyntö: %s\n\n"
       "Ilmoittaja: %s\n\n"
       "Lähettäjä: %s\n\n"
       "Paikka: %s\n\n"
       "TR-osoite: %s\n\n"
       "Selitteet: %s.\n\n"
       "Lisätietoja: %s.\n\n"
       "Kuittauskoodit:\n"
       "V%s = vastaanotettu\n"
       "A%s = aloitettu\n"
       "L%s = lopetettu\n"
       "M%s = muutettu\n"
       "R%s = vastattu\n"
       "U%s = väärä urakka\n\n"
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
    "M" "muutos"
    "R" "vastaus"
    "U" "vaara-urakka"
    (throw+ {:type :tuntematon-ilmoitustoimenpide
             :virheet [{:koodi :tuntematon-ilmoitustoimenpide
                        :viesti (format "Tuntematon ilmoitustoimenpide: %s" toimenpide)}]})))

(defn parsi-viestinumero [numero]
  (if (merkkijono/parsittavissa-intiksi? numero)
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
    (let [{:keys [toimenpide vapaateksti viestinumero]} (parsi-tekstiviesti viesti)
          {:keys [ilmoitus ilmoitusid yhteyshenkilo]} (hae-paivystajatekstiviesti db viestinumero puhelinnumero)
          paivystaja (first (yhteyshenkilot/hae-yhteyshenkilo db yhteyshenkilo ))
          tallenna (fn [toimenpide vapaateksti]
                     (ilmoitustoimenpiteet/tallenna-ilmoitustoimenpide
                       db
                       ilmoitus
                       ilmoitusid
                       vapaateksti
                       toimenpide
                       paivystaja
                       "sisaan"
                       "sms"))]

      (when (and (= toimenpide "aloitus") (not (ilmoitukset/ilmoitukselle-olemassa-vastaanottokuittaus? db ilmoitusid)))
        (let [aloitus-kuittaus-id (tallenna "vastaanotto" "Vastaanotettu")]
          (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja db aloitus-kuittaus-id)))

      (let [ilmoitustoimenpide-id (tallenna toimenpide vapaateksti)]
        (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja db ilmoitustoimenpide-id))

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

(defn ilmoitus-tekstiviesti [ilmoitus viestinumero]
  (let [tunniste (:tunniste ilmoitus)
        otsikko (:otsikko ilmoitus)
        paikankuvaus (:paikankuvaus ilmoitus)
        tr-osoite (tierekisteri/tierekisteriosoite-tekstina
                    (:sijainti ilmoitus)
                    {:teksti-tie? false})
        lisatietoja (if (:lisatieto ilmoitus)
                      (merkkijono/leikkaa 500 (:lisatieto ilmoitus))
                      "")
        selitteet (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))
        virka-apupyynto (if (ilm/virka-apupyynto? ilmoitus) "(VIRKA-APUPYYNTÖ)" "")]
    (str
      (format +ilmoitusviesti+
              virka-apupyynto
              otsikko
              viestinumero
              tunniste
              (:urakkanimi ilmoitus)
              (fmt/totuus (:yhteydenottopyynto ilmoitus))
              (apurit/nayta-henkilon-yhteystiedot (:ilmoittaja ilmoitus))
              (apurit/nayta-henkilon-yhteystiedot (:lahettaja ilmoitus))
              paikankuvaus
              tr-osoite
              selitteet
              lisatietoja
              viestinumero
              viestinumero
              viestinumero
              viestinumero
              viestinumero
              viestinumero))))

(defn laheta-ilmoitus-tekstiviestilla [sms db ilmoitus paivystaja]
  (try
    (if-let [puhelinnumero (or (:matkapuhelin paivystaja) (:tyopuhelin paivystaja))]
      (do
        (log/debug (format "Lähetetään ilmoitus (id: %s) tekstiviestillä numeroon: %s"
                           (:ilmoitus-id ilmoitus) puhelinnumero))
        (let [viestinumero (paivystajatekstiviestit/kirjaa-uusi-viesti
                             db (:id paivystaja) (:ilmoitus-id ilmoitus) puhelinnumero)
              viesti (ilmoitus-tekstiviesti ilmoitus viestinumero)]
          (sms/laheta sms puhelinnumero viesti)

          (ilmoitustoimenpiteet/tallenna-ilmoitustoimenpide
            db
            (:id ilmoitus)
            (:ilmoitus-id ilmoitus)
            viesti
            "valitys"
            paivystaja
            "ulos"
            "sms")))
      (log/warn "Ilmoitusta ei voida lähettää tekstiviestillä ilman puhelinnumeroa."))
    (catch Exception e
      (log/error "Ilmoituksen lähettämisessä tekstiviestillä tapahtui poikkeus." e))))
