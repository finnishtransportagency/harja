(ns harja.palvelin.integraatiot.tloik.tloik-komponentti
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.tloik.ilmoitukset :as ilmoitukset]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma :as tloik-kuittaus-sanoma]))

(defprotocol Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]))

(defn tee-lokittaja [this]
  (integraatioloki/lokittaja (:integraatioloki this) (:db this) "tloik" "toimenpiteen-lahetys"))

(defn tee-ilmoitusviestikuuntelija [this ilmoitusviestijono ilmoituskuittausjono]
  (when (and ilmoitusviestijono (not (empty? ilmoituskuittausjono)))
    (log/debug "Käynnistetään T-LOIK:n Sonja viestikuuntelija kuuntelemaan jonoa: " ilmoitusviestijono)
    (sonja/kuuntele (:sonja this) ilmoitusviestijono
                    (fn [viesti]
                      (ilmoitukset/vastaanota-ilmoitus (:sonja this) (tee-lokittaja this) (:db this) ilmoituskuittausjono viesti)))))

(defn tee-toimenpidekuittauskuuntelija [this toimenpidekuittausjono]
  (when (and toimenpidekuittausjono (not (empty? toimenpidekuittausjono)))
    (jms/kuittausjonokuuntelija (tee-lokittaja this) (:sonja this) toimenpidekuittausjono
                                (fn [kuittaus] (tloik-kuittaus-sanoma/lue-kuittaus kuittaus))
                                :viesti-id
                                (comp not :virhe)
                                (fn [_ viesti-id onnistunut]
                                  (ilmoitustoimenpiteet/vastaanota-kuittaus (:db this) viesti-id onnistunut)))))

(defrecord Tloik [ilmoitusviestijono ilmoituskuittausjono toimenpidejono toimenpidekuittausjono]
  component/Lifecycle
  (start [this]
    (-> this
        (assoc :sonja-ilmoitusviestikuuntelija (tee-ilmoitusviestikuuntelija this ilmoitusviestijono ilmoituskuittausjono))
        (assoc :sonja-toimenpidekuittauskuuntelija (tee-toimenpidekuittauskuuntelija this toimenpidekuittausjono))))
  (stop [this]
    (let [poista-ilmoitusviestikuuntelija (:sonja-ilmoitusviestikuuntelija this)
          poista-toimenpidekuittauskuuntelija (:sonja-toimenpidekuittauskuuntelija this)]
      (poista-ilmoitusviestikuuntelija)
      (poista-toimenpidekuittauskuuntelija))
    this)

  Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]
    (let [jms-lahettaja (jms/jonolahettaja (tee-lokittaja this) (:sonja this) toimenpidejono)]
      (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja (:db this) id))))