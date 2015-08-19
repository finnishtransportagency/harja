(ns harja.palvelin.palvelut.tyokoneenseuranta
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.konversio :as konversiot]
            [harja.kyselyt.tyokoneseuranta :as tks]))

(defn- formatoi-vastaus [tyokone]
  (-> tyokone
      (update-in [:sijainti] geo/pg->clj)
      (assoc :tyyppi :tyokone)
      (konversiot/array->set :tehtavat)))

(defn hae-tyokoneet-alueelta [db user hakuehdot]
  (let [alue (:alue hakuehdot)
        urakka (:urakka hakuehdot)]
    (log/debug "Haetaan tyokoneet alueelta: " alue " urakka " urakka)
    (map formatoi-vastaus (if urakka
                            (tks/urakan-tyokoneet-alueella db
                                                           urakka
                                                           (:ymin alue)
                                                           (:xmin alue)
                                                           (:ymax alue)
                                                           (:xmax alue))
                            (tks/tyokoneet-alueella db
                                                    (:ymin alue)
                                                    (:xmin alue)
                                                    (:ymax alue)
                                                    (:xmax alue))))))

(defrecord TyokoneseurantaHaku []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :hae-tyokoneseurantatiedot
                        (fn [user haku]
                          (vec (hae-tyokoneet-alueelta (:db this) user haku)))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-tyokoneseurantatiedot)
    this))

