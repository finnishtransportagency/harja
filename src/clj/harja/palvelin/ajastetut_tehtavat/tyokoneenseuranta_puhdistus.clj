(ns harja.palvelin.ajastetut-tehtavat.tyokoneenseuranta-puhdistus
  (:require [harja.kyselyt.tyokoneseuranta :as tks]
            [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<! go-loop]]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]))

(defn poista-vanhat-tyokonesijainnit [db]
  (log/debug "Poistetaan vanhentuneet työkonehavainnot")
  (tks/poista-vanhentuneet-havainnot! db))

(defrecord TyokoneenseurantaPuhdistus []
  component/Lifecycle
  (start [this]
    (assoc this
      ::poista-ajastus
      (ajastettu-tehtava/ajasta-minuutin-valein
        15
        (fn [_] (poista-vanhat-tyokonesijainnit (:db this))))))
  (stop [this]
    ((::poista-ajastus this))
    this))
