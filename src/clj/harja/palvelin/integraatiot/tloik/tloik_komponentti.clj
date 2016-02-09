(ns harja.palvelin.integraatiot.tloik.tloik-komponentti
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.tloik
             [ilmoitukset :as ilmoitukset]
             [ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
             [kuittaukset :as kuittaukset]]
            [harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma
             :as
             tloik-kuittaus-sanoma]
            [harja.palvelin.komponentit.sonja :as sonja]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]))

(defprotocol Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]))

(defn tee-lokittaja [this]
  (integraatioloki/lokittaja (:integraatioloki this) (:db this) "tloik" "toimenpiteen-lahetys"))

(defn tee-ilmoitusviestikuuntelija [this ilmoitusviestijono ilmoituskuittausjono]
  (when (and ilmoitusviestijono (not (empty? ilmoituskuittausjono)))
    (log/debug "Käynnistetään T-LOIK:n Sonja viestikuuntelija kuuntelemaan jonoa: " ilmoitusviestijono)
    (sonja/kuuntele (:sonja this) ilmoitusviestijono
                    (fn [viesti]
                      (ilmoitukset/vastaanota-ilmoitus (:sonja this)
                                                       (tee-lokittaja this)
                                                       (:klusterin-tapahtumat this)
                                                       (:db this)
                                                       ilmoituskuittausjono viesti)))))

(defn tee-toimenpidekuittauskuuntelija [this toimenpidekuittausjono]
  (when (and toimenpidekuittausjono (not (empty? toimenpidekuittausjono)))
    (jms/kuittausjonokuuntelija (tee-lokittaja this) (:sonja this) toimenpidekuittausjono
                                (fn [kuittaus] (tloik-kuittaus-sanoma/lue-kuittaus kuittaus))
                                :viesti-id
                                (comp not :virhe)
                                (fn [_ viesti-id onnistunut]
                                  (ilmoitustoimenpiteet/vastaanota-kuittaus (:db this) viesti-id onnistunut)))))


(defrecord Tloik [jonot]
  component/Lifecycle
  (start [this]
    (let [{:keys [ilmoitusviestijono ilmoituskuittausjono toimenpidejono toimenpidekuittausjono
                  sahkoposti-sisaan-jono sahkoposti-sisaan-kuittausjono]} jonot]
      (assoc this
             :sonja-ilmoitusviestikuuntelija (tee-ilmoitusviestikuuntelija this ilmoitusviestijono ilmoituskuittausjono)
             :sonja-toimenpidekuittauskuuntelija (tee-toimenpidekuittauskuuntelija this toimenpidekuittausjono)
             :sonja-sahkopostikuittauskuuntelija (tee-sahkopostikuittauskuuntelija this sahkoposti-sisaan-jono sahkoposti-sisaan-kuittausjono))))
  (stop [this]
    (let [kuuntelijat [:sonja-ilmoitusviestikuuntelija
                       :sonja-toimenpidekuittauskuuntelija
                       :sonja-sahkopostikuittauskuuntelija]]
      (doseq [kuuntelija kuuntelijat
              :let [poista-kuuntelija-fn (get this kuuntelija)]]
        (poista-kuuntelija-fn))
      (apply dissoc this kuuntelijat)))

  Ilmoitustoimenpidelahetys
  (laheta-ilmoitustoimenpide [this id]
    (let [jms-lahettaja (jms/jonolahettaja (tee-lokittaja this) (:sonja this) (:toimenpidejono jonot))]
      (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja (:db this) id))))
