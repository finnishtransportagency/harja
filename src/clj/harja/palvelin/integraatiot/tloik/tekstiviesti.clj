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
       "Huom: tekstiviestikuittaus on poistunut käytöstä. Kuittaa viestit Harjassa tai sähköpostilla.\n"))

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
    "K" "toimenpiteet-kaynnissa"
    "L" "lopetus"
    "T" "lopetus"
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

(defn parsi-tekstiviesti [viesti]
  (when (> 2 (count viesti))
    (throw+ {:type :viestinumero-tai-toimenpide-puuttuu}))
  (let [;; osat ovat: 1. toimenpide joka on 1 merkki (.{1})
        ;;            2. viestinumero ([0-9]+)
        ;;            3. vapaateksti (.+)
        osat (re-find #"^(.{1})([0-9]+)(.*)" viesti)
        toimenpidelyhenne (nth osat 1)
        toimenpide (parsi-toimenpide toimenpidelyhenne)
        viestinumero (parsi-viestinumero (nth osat 2))
        vapaateksti (.trim (nth osat 3))]
    {:toimenpide toimenpide
     :viestinumero viestinumero
     :vapaateksti vapaateksti
     :aiheutti-toimenpiteita (= toimenpidelyhenne "T")}))

(defn hae-paivystajatekstiviesti [db viestinumero puhelinnumero]
  (if-let [paivystajatekstiviesti (first (paivystajatekstiviestit/hae-puhelin-ja-viestinumerolla db puhelinnumero viestinumero))]
    paivystajatekstiviesti
    (throw+ {:type :tuntematon-ilmoitus})))

(defn vastaanota-tekstiviestikuittaus [jms-lahettaja db puhelinnumero viesti]
  (log/warn (format "Vastaanotettiin T-LOIK kuittaus tekstiviestillä. Numero: %s, viesti: %s." puhelinnumero viesti))
  (try+
    (let [{:keys [toimenpide vapaateksti viestinumero aiheutti-toimenpiteita]} (parsi-tekstiviesti viesti)
          {:keys [ilmoitus ilmoitusid yhteyshenkilo]} (hae-paivystajatekstiviesti db viestinumero puhelinnumero)
          paivystaja (first (yhteyshenkilot/hae-yhteyshenkilo db yhteyshenkilo))
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

      (if (= toimenpide "toimenpiteet-kaynnissa")
        (when-let [id (:id (first (ilmoitukset/hae-id-ilmoitus-idlla db ilmoitusid)))]
          (ilmoitukset/tallenna-ilmoitusten-toimenpiteiden-aloitukset! db [id]))
        (let [ilmoitustoimenpide-id (tallenna toimenpide vapaateksti)]
          (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja db ilmoitustoimenpide-id)))

      (when aiheutti-toimenpiteita
        (ilmoitukset/ilmoitus-aiheutti-toimenpiteita! db true ilmoitus))

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
              lisatietoja))))

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
