(ns harja.palvelin.integraatiot.api.tyokoneenseuranta-puhdistus
  (:require [harja.kyselyt.tyokoneseuranta :as tks]
            [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clojure.core.async :as a :refer [<! go-loop]]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]))

(defn poista-vanhat-tyokonesijainnit [db]
  (log/debug "poistetaan vanhentuneet työkonehavainnot")
  (tks/poista-vanhentuneet-havainnot! db))

(defrecord TyokoneenseurantaPuhdistus []
  component/Lifecycle
  (start [this]
    (assoc this
           ::poista-ajastus
           (ajastettu-tehtava/ajasta-minuutin-valein
             15
             #(poista-vanhat-tyokonesijainnit (:db this)))))
  (stop [this]
    ((::poista-ajastus this))
    this))
