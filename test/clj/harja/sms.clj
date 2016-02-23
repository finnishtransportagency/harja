(ns harja.sms
  (:require [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(defrecord FeikkiLabyrintti []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  sms/Sms
  (rekisteroi-kuuntelija! [this kasittelija]
    (log/info "Feikki Labyrintti EI tue kuuntelijan rekisteröintiä")
    #(log/info "Poistetaan muka Feikki Labyrintin kuuntelija"))
  (laheta [this numero viesti]
    (log/info "Feikki Labyrintti lähettää muka viestin numeroon " numero ": " viesti)))

(defn feikki-labyrintti []
  (->FeikkiLabyrintti))

