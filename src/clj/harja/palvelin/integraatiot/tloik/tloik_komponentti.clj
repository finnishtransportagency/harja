(ns harja.palvelin.integraatiot.tloik.tloik-komponentti
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.sonja :as sonja]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma :as tloik-kuittaus-sanoma]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik
             [ilmoitukset :as ilmoitukset]
             [ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
             [tekstiviesti :as tekstiviesti]
             [sahkoposti :as sahkopostiviesti]]))

(defprotocol Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]))

(defn tee-lokittaja [this integraatio]
  (integraatioloki/lokittaja (:integraatioloki this) (:db this) "tloik" integraatio))

(defn tee-ilmoitusviestikuuntelija [{:keys [db sonja klusterin-tapahtumat] :as this}
                                    ilmoitusviestijono ilmoituskuittausjono ilmoitusasetukset]
  (when (and ilmoitusviestijono (not (empty? ilmoituskuittausjono)))
    (log/debug "Käynnistetään T-LOIK:n Sonja viestikuuntelija kuuntelemaan jonoa: " ilmoitusviestijono)
    (sonja/kuuntele
      sonja ilmoitusviestijono
      (partial ilmoitukset/vastaanota-ilmoitus
               sonja (tee-lokittaja this "ilmoituksen-kirjaus")
               ilmoitusasetukset klusterin-tapahtumat db ilmoituskuittausjono))))

(defn tee-toimenpidekuittauskuuntelija [this toimenpidekuittausjono]
  (when (and toimenpidekuittausjono (not (empty? toimenpidekuittausjono)))
    (jms/kuittausjonokuuntelija
      (tee-lokittaja this "toimenpiteen-lahetys") (:sonja this) toimenpidekuittausjono
      (fn [kuittaus] (tloik-kuittaus-sanoma/lue-kuittaus kuittaus))
      :viesti-id
      (comp not :virhe)
      (fn [_ viesti-id onnistunut]
        (ilmoitustoimenpiteet/vastaanota-kuittaus (:db this) viesti-id onnistunut)))))

(defn rekisteroi-kuittauskuuntelijat [{:keys [sonja labyrintti db sonja-sahkoposti] :as this} jonot]
  (let [jms-lahettaja (jms/jonolahettaja (tee-lokittaja this "toimenpiteen-lahetys")
                                         sonja (:toimenpideviestijono jonot))]
    (when-let [labyrintti labyrintti]
      (log/debug "---> Yhdistetään kuuntelija Labyritin SMS Gatewayhyn")
      (sms/rekisteroi-kuuntelija! labyrintti
                                  (fn [numero viesti]
                                    (tekstiviesti/vastaanota-tekstiviestikuittaus jms-lahettaja db numero viesti))))
    (when-let [sonja-sahkoposti sonja-sahkoposti]
      (log/debug "---> Yhdistetään kuuntelija Sonjan sähköpostijonoihin")
      (sahkoposti/rekisteroi-kuuntelija!
        sonja-sahkoposti
        (fn [viesti]
          (when-let [vastaus (sahkopostiviesti/vastaanota-sahkopostikuittaus jms-lahettaja db viesti)]
            (sahkoposti/laheta-viesti! sonja-sahkoposti (sahkoposti/vastausosoite sonja-sahkoposti)
                                       (:lahettaja viesti)
                                       (:otsikko vastaus) (:sisalto vastaus))))))))

(defrecord Tloik [asetukset]
  component/Lifecycle
  (start [{:keys [labyrintti sonja-sahkoposti] :as this}]
    (log/debug "---> Käynnistetään T-LOIK komponentti")
    (rekisteroi-kuittauskuuntelijat this asetukset)
    (let [{:keys [ilmoitusviestijono ilmoituskuittausjono toimenpidekuittausjono]} asetukset
          ilmoitusasetukset (merge (:ilmoitukset asetukset)
                                   {:sms   labyrintti
                                    :email sonja-sahkoposti})]
      (assoc this
        :sonja-ilmoitusviestikuuntelija (tee-ilmoitusviestikuuntelija this ilmoitusviestijono ilmoituskuittausjono ilmoitusasetukset)
        :sonja-toimenpidekuittauskuuntelija (tee-toimenpidekuittauskuuntelija this toimenpidekuittausjono))))
  (stop [this]
    (let [kuuntelijat [:sonja-ilmoitusviestikuuntelija
                       :sonja-toimenpidekuittauskuuntelija]]
      (doseq [kuuntelija kuuntelijat
              :let [poista-kuuntelija-fn (get this kuuntelija)]]
        (poista-kuuntelija-fn))
      (apply dissoc this kuuntelijat)))
  Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]
    (let [jms-lahettaja (jms/jonolahettaja (tee-lokittaja this "toimenpiteen-lahetys") (:sonja this) (:toimenpideviestijono asetukset))]
      (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja (:db this) id))))
