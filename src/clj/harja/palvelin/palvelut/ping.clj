(ns harja.palvelin.palvelut.ping
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]))

(defn kasittele-ping [db user tiedot]
  :pong)

(defrecord Ping []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :ping
                        (fn [user tiedot]
                          (oikeudet/ei-oikeustarkistusta!)
                          (kasittele-ping db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :ping)
    this))
