(ns harja.palvelin.integraatiot.tloik.tloik-komponentti
  "T-LOIKin käyttöön tarvittavat palvelut.
  T-LOIK on Tieliikennekeskuksen järjestelmä, missä ilmoitukset kirjataan ja välitetään Harjaan.

  Tieliikennekeskuksen vastaanottamat ilmoitukset kirjataan T-LOIK-järjestelmään
  (Tieliikennekeskuksen integroitu käyttöliittymä), josta ilmoitukset välitetään Harjaan.
  Harjan vastuulla on päätellä annetun ilmoituksen käsittelevä urakka urakkatyypin
  ja sijannin perusteella. Urakoitsijat lähettävät kuittauksia eli ns. ilmoitustoimenpiteitä
  esim. vastaamaan kysymyksiin sekä kertomaaan työn edistymisestä."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.sonja :as sonja]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [hiccup.core :refer [html h]]
            [harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma :as tloik-kuittaus-sanoma]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik
             [ilmoitukset :as ilmoitukset]
             [ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
             [tekstiviesti :as tekstiviesti]
             [sahkoposti :as sahkopostiviesti]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as ominaisuudet]))

(defprotocol Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]))

(defn tee-lokittaja [this integraatio]
  (integraatioloki/lokittaja (:integraatioloki this) (:db this) "tloik" integraatio))

(defn tee-ilmoitusviestikuuntelija [{:keys [db sonja] :as this}
                                    ilmoitusviestijono ilmoituskuittausjono ilmoitusasetukset
                                    jms-lahettaja kehitysmoodi?]
  (when (and ilmoitusviestijono (not (empty? ilmoituskuittausjono)))
    (log/debug "Käynnistetään T-LOIK:n Sonja viestikuuntelija kuuntelemaan jonoa: " ilmoitusviestijono)
    (sonja/kuuntele!
      sonja ilmoitusviestijono
      (partial ilmoitukset/vastaanota-ilmoitus
               sonja (tee-lokittaja this "ilmoituksen-kirjaus")
               ilmoitusasetukset db ilmoituskuittausjono
               jms-lahettaja kehitysmoodi?))))

(defn tee-toimenpidekuittauskuuntelija [this toimenpidekuittausjono]
  (when (and toimenpidekuittausjono (not (empty? toimenpidekuittausjono)))
    (jms/kuittausjonokuuntelija
      (tee-lokittaja this "toimenpiteen-lahetys") (:sonja this) toimenpidekuittausjono
      (fn [kuittaus] (tloik-kuittaus-sanoma/lue-kuittaus kuittaus))
      :viesti-id
      #(and (not (:virhe %)) (not (= "virhe" (:kuittaustyyppi %))))
      (fn [_ viesti-id onnistunut]
        (ilmoitustoimenpiteet/vastaanota-kuittaus (:db this) viesti-id onnistunut)))))

(defn rekisteroi-kuittauskuuntelijat [{:keys [sonja labyrintti db sonja-sahkoposti] :as this} jonot]
  (let [jms-lahettaja (jms/jonolahettaja (tee-lokittaja this "toimenpiteen-lahetys")
                                         sonja (:toimenpideviestijono jonot))]
    (when-let [labyrintti labyrintti]
      (log/debug "Yhdistetään kuuntelija Labyritin SMS Gatewayhyn")
      (sms/rekisteroi-kuuntelija! labyrintti
                                  (fn [numero viesti]
                                    (tekstiviesti/vastaanota-tekstiviestikuittaus jms-lahettaja db numero viesti))))
    (when-let [sonja-sahkoposti sonja-sahkoposti]
      (log/debug "Yhdistetään kuuntelija Sonjan sähköpostijonoihin")
      (sahkoposti/rekisteroi-kuuntelija!
        sonja-sahkoposti
        (fn [viesti]
          (try
            (when-let [vastaus (sahkopostiviesti/vastaanota-sahkopostikuittaus jms-lahettaja db viesti)]
              (sahkoposti/laheta-viesti! sonja-sahkoposti (sahkoposti/vastausosoite sonja-sahkoposti)
                                         (:lahettaja viesti)
                                         (:otsikko vastaus)
                                         (:sisalto vastaus)))
            (catch Throwable t
              (log/error t "Virhe T-LOIK kuittaussähköpostin vastaanotossa"))))))))

(defn tee-ilmoitustoimenpide-jms-lahettaja [this asetukset]
  (jms/jonolahettaja (tee-lokittaja this "toimenpiteen-lahetys") (:sonja this) (:toimenpideviestijono asetukset)))


(defn tee-ajastettu-uudelleenlahetys-tehtava [this toimenpide-jms-lahettaja aikavali]
  (if aikavali
    (do
      (log/debug (format "Ajastetaan lähettämättömien T-LOIK kuittausten lähetys ajettavaksi: %s minuutin välein." aikavali))
      (ajastettu-tehtava/ajasta-minuutin-valein
        aikavali 12 ;; 12 sekunnin käynnistysviive, jonka jälkeen "aikavali" minuutin välein
        (fn [_]
          (ilmoitustoimenpiteet/laheta-lahettamattomat-ilmoitustoimenpiteet toimenpide-jms-lahettaja (:db this)))))
    (constantly nil)))

(defrecord Tloik [asetukset kehitysmoodi?]
  component/Lifecycle
  (start [{:keys [labyrintti sonja-sahkoposti] :as this}]
    (log/debug "Käynnistetään T-LOIK komponentti")
    (rekisteroi-kuittauskuuntelijat this asetukset)
    (let [{:keys [ilmoitusviestijono ilmoituskuittausjono toimenpidekuittausjono
                  uudelleenlahetysvali-minuuteissa]} asetukset
          ilmoitusasetukset (merge (:ilmoitukset asetukset)
                                   {:sms labyrintti
                                    :email sonja-sahkoposti})
          toimenpide-jms-lahettaja (tee-ilmoitustoimenpide-jms-lahettaja this asetukset)]
      (assoc this
        :sonja-ilmoitusviestikuuntelija (tee-ilmoitusviestikuuntelija
                                          this
                                          ilmoitusviestijono
                                          ilmoituskuittausjono
                                          ilmoitusasetukset
                                          toimenpide-jms-lahettaja
                                          kehitysmoodi?)
        :sonja-toimenpidekuittauskuuntelija (tee-toimenpidekuittauskuuntelija
                                              this
                                              toimenpidekuittausjono)
        :paivittainen-lahetys-tehtava (tee-ajastettu-uudelleenlahetys-tehtava
                                        this
                                        toimenpide-jms-lahettaja
                                        uudelleenlahetysvali-minuuteissa))))
  (stop [this]
    (let [kuuntelijat [:sonja-ilmoitusviestikuuntelija
                       :sonja-toimenpidekuittauskuuntelija
                       :paivittainen-lahetys-tehtava]]
      (doseq [kuuntelija kuuntelijat
              :let [poista-kuuntelija-fn (get this kuuntelija)]]
        (when poista-kuuntelija-fn
          (poista-kuuntelija-fn)))
      (apply dissoc this kuuntelijat)))

  Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]
    (let [jms-lahettaja (tee-ilmoitustoimenpide-jms-lahettaja this asetukset)]
      (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja (:db this) id))))
