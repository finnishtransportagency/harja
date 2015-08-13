(ns harja.palvelin.palvelut.tyokoneenseuranta
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konversiot]
            [harja.kyselyt.tyokoneseuranta :as tks]))

(defn- formatoi-vastaus [tyokone]
  (-> tyokone
      (update-in [:sijainti] geo/pg->clj)
      (konversiot/array->set :tehtavat)))

(defn hae-tyokoneet-alueelta [db user hakuehdot]
  (map formatoi-vastaus (tks/tyokoneet-alueella db
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
                          (vec (hae-tyokoneet-alueelta (:db this) user haku)))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-tyokoneseurantatiedot)
    this))

(def foo (hae-tyokoneet-alueelta (harja.testi/luo-testitietokanta) "jvh" {:xmin 0 :ymin 0 :xmax 9000000 :ymax 9000000}))
