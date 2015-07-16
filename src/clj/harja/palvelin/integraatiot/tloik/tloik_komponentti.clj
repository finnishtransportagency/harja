(ns harja.palvelin.integraatiot.tloik.tloik-komponentti
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.tloik.ilmoitukset :as ilmoitukset]))

(defn tee-sonja-ilmoitusviestikuuntelija [this ilmoitusviestijono ilmoituskuittausjono]
  (when ilmoitusviestijono
    (log/debug "Käynnistetään T-LOIK:n Sonja viestikuuntelija kuuntelemaan jonoa: " ilmoitusviestijono)
    (sonja/kuuntele (:sonja this) ilmoitusviestijono
                    (fn [viesti]
                      (ilmoitukset/vastaanota-ilmoitus (:sonja this) (:integraatioloki this) (:db this) ilmoituskuittausjono viesti)))))

(defrecord Tloik [ilmoitusviestijono ilmoituskuittausjono]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (assoc this :sonja-ilmoitusviestikuuntelija (tee-sonja-ilmoitusviestikuuntelija this ilmoitusviestijono ilmoituskuittausjono)))
  (stop [this]
    (let [poista-ilmoitusviestikuuntelija (:sonja-ilmoitusviestikuuntelija this)]
      (poista-ilmoitusviestikuuntelija))
    this))