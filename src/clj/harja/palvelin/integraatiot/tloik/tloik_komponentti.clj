(ns harja.palvelin.integraatiot.tloik.tloik-komponentti
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.tloik.ilmoitukset :as ilmoitukset]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]))

(defprotocol Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]))

(defn tee-sonja-ilmoitusviestikuuntelija [this ilmoitusviestijono ilmoituskuittausjono]
  (when (and ilmoitusviestijono (not (empty? ilmoituskuittausjono)))
    (log/debug "Käynnistetään T-LOIK:n Sonja viestikuuntelija kuuntelemaan jonoa: " ilmoitusviestijono)
    (sonja/kuuntele (:sonja this) ilmoitusviestijono
                    (fn [viesti]
                      (ilmoitukset/vastaanota-ilmoitus (:sonja this) (:integraatioloki this) (:db this) ilmoituskuittausjono viesti)))))

(defn tee-sonja-toimenpidekuittauskuuntelija [this toimenpidekuittausjono]
  (when (and toimenpidekuittausjono (not (empty? toimenpidekuittausjono)))
    (log/debug "Käynnistetään T-LOIK:n Sonja toimenpidekuittauskuuntelija kuuntelemaan jonoa: " toimenpidekuittausjono)
    (sonja/kuuntele (:sonja this) toimenpidekuittausjono
                    (fn [viesti]
                      (ilmoitustoimenpiteet/vastaanota-kuittaus (:integraatioloki this) (:db this) viesti)))))

(defrecord Tloik [ilmoitusviestijono ilmoituskuittausjono toimenpidejono toimenpidekuittausjono]
  component/Lifecycle
  (start [this]
    (assoc this :sonja-ilmoitusviestikuuntelija (tee-sonja-ilmoitusviestikuuntelija this ilmoitusviestijono ilmoituskuittausjono))
    (assoc this :sonja-toimenpidekuittauskuuntelija (tee-sonja-toimenpidekuittauskuuntelija this toimenpidekuittausjono)))
  (stop [this]
    (let [poista-ilmoitusviestikuuntelija (:sonja-ilmoitusviestikuuntelija this)
          poista-toimenpidekuittauskuuntelija (:sonja-toimenpidekuittauskuuntelija this)]
      (poista-ilmoitusviestikuuntelija)
      (poista-toimenpidekuittauskuuntelija))
    this)

  Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]
    (let [jms-lahettaja (jms/jono-lahettaja (:integraatioloki this) (:sonja this) toimenpidejono "tloik" "toimenpiteen-lahetys")]
      (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja (:db this) id))))