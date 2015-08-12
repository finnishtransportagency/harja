(ns harja.palvelin.palvelut.tyokoneenseuranta
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [harja.kyselyt.tyokoneseuranta :as tks]))

(defn hae-tyokoneet-alueelta [db user hakuehdot]
  (into []
        (tks/tyokoneet-alueella db
                                (:xmin hakuehdot)
                                (:ymin hakuehdot)
                                (:xmax hakuehdot)
                                (:ymax hakuehdot))))

(defrecord TyokoneseurantaHaku []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :hae-tyokoneseurantatiedot
                        (fn [user haku]
                          (hae-tyokoneet-alueelta (:db this) user haku))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-tyokoneseurantatiedot)
    this))
