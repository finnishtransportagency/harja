(ns harja.palvelin.integraatiot.sahke.sahke-komponentti
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.sahke.sanomat.urakkasanoma :as urakkasanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava])
  (:import (java.util UUID)))

(defprotocol Valtuushallinta
  (laheta-urakka-sahkeeseen [this urakka-id]))

(defn laheta-urakka [jms-lahettaja db urakka-id]
  (log/info (format "Lähetetään urakka: %s Sähkeeseen" urakka-id))
  (let [viesti-id (str (UUID/randomUUID))
        urakka (first (q-urakat/hae-urakka-lahetettavaksi-sahkeeseen db urakka-id))
        muodosta-xml #(urakkasanoma/muodosta urakka viesti-id)]
    (try
      (jms-lahettaja muodosta-xml viesti-id)
      (log/debug (format "Urakan (id: %s) lähetys Sähkeeseen onnistui." urakka-id))
      (q-urakat/kirjaa-sahke-lahetys! db urakka-id true)

      (catch Exception e
        (log/error e (format "Urakan (id: %s) lähetys Sähkeeseen epäonnistui." urakka-id))
        (q-urakat/kirjaa-sahke-lahetys! db urakka-id false)))))

(defn tee-jms-lahettaja [sonja integraatioloki db lahetysjono]
  (jms/jonolahettaja (integraatioloki/lokittaja integraatioloki db "sahke" "urakan-lahetys") sonja lahetysjono))

(defn laheta-epaonnistuneet-urakat-uudestaan [sonja integraatioloki db lahetysjono]
  (let [urakka-idt (q-urakat/hae-urakat-joiden-lahetys-sahkeeseen-epaonnistunut db)
        jms-lahettaja (tee-jms-lahettaja sonja integraatioloki db lahetysjono)]
    (doseq [urakka-id urakka-idt]
      (laheta-urakka jms-lahettaja db urakka-id))))

(defn tee-uudelleenlahetys-tehtava [{:keys [sonja db integraatioloki]} uudelleenlahetysaika lahetysjono]
  (if uudelleenlahetysaika
    (do
      (log/debug "Ajastetaan urakoiden uudelleenlähetys Sähkeeseen tehtäväksi joka päivä kello: " uudelleenlahetysaika)
      (ajastettu-tehtava/ajasta-paivittain
        uudelleenlahetysaika
        (fn [_] (laheta-epaonnistuneet-urakat-uudestaan sonja db integraatioloki lahetysjono))))
    (fn [])))

(defrecord Sahke [lahetysjono uudelleenlahetysaika]
  component/Lifecycle
  (start [this]
    (log/info (format "Käynnistetään Sähke-komponentti. JMS-jono: %s. Uudelleenlähetykset: %s."
                      lahetysjono
                      uudelleenlahetysaika))

    (assoc this :uudelleenlahetys-tehtava (tee-uudelleenlahetys-tehtava this uudelleenlahetysaika lahetysjono)))
  (stop [this]
    (log/info "Sammutetaan Sähke-komponentti")
    (:uudelleenlahetys-tehtava this)
    this)

  Valtuushallinta
  (laheta-urakka-sahkeeseen [{:keys [sonja db integraatioloki]} urakka-id]
    (let [jms-lahettaja (tee-jms-lahettaja sonja integraatioloki db lahetysjono)]
      (laheta-urakka jms-lahettaja db urakka-id))))
