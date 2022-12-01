(ns harja.palvelin.palvelut.info
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.info :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-koulutusvideot [db]
  (oikeudet/ei-oikeustarkistusta!)
  (into []
        (q/hae-koulutusvideot db)))

(defrecord Info []
  component/Lifecycle
  (start [this]
         (julkaise-palvelu (:http-palvelin this)
                           :hae-koulutusvideot
                           (fn []
                             (hae-koulutusvideot (:db this))))
         this)

  (stop [this]
        (poista-palvelut (:http-palvelin this)
                         :hae-koulutusvideot)
        this))