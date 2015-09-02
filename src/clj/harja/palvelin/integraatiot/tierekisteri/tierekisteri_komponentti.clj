(ns harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti
  (:require [harja.palvelin.integraatiot.tierekisteri.tietolajit :as tietolajit]
            [com.stuartsierra.component :as component]))

(defprotocol TierekisteriPalvelut
  (hae-tietolajit [this tunniste]))

(defrecord Tierekisteri [tierekisteri-api-url]
  component/Lifecycle
  (start [this])
  (stop [this] this)

  TierekisteriPalvelut
  (hae-tietolajit [this tunniste]
    (let [tietolajien-haku (if (not (empty? tierekisteri-api-url))
                             (tietolajit/hae-tietolajit (:integraatioloki this) tierekisteri-api-url tunniste)
                             (fn []))]
      {:tietolajien-haku tietolajien-haku})))

