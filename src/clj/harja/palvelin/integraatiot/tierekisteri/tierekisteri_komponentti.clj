(ns harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti
  (:require
    [com.stuartsierra.component :as component]
    [harja.palvelin.integraatiot.tierekisteri.tietolajit :as tietolajit]))

(defprotocol TierekisteriPalvelut
  (hae-tietolajit [this tunniste muutospvm]))

(defrecord Tierekisteri [tierekisteri-api-url]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  TierekisteriPalvelut
  (hae-tietolajit [this tunniste muutospvm]
    (let [tietolajien-haku
          (if (not (empty? tierekisteri-api-url))
            (tietolajit/hae-tietolajit (:integraatioloki this) tierekisteri-api-url tunniste muutospvm)
            (fn []))]
      {:tietolajien-haku tietolajien-haku})))

