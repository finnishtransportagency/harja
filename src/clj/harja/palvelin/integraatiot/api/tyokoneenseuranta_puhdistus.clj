(ns harja.palvelin.integraatiot.api.tyokoneenseuranta-puhdistus
  (:require [harja.kyselyt.tyokoneseuranta :as tks]
            [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clojure.core.async :as a :refer [<! go-loop]]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]))

(def vanhojen-poisto-ajat (chime-ch (periodic-seq (t/now) (t/minutes 1))))

(defn poista-vanhat-tyokonesijainnit [db]
  (log/debug "poistetaan vanhentuneet työkonehavainnot")
  (tks/poista-vanhentuneet-havainnot! db))

(defn pysayta-vanhojen-poisto []
  (a/close! vanhojen-poisto-ajat))

(defn aloita-vanhojen-poisto [db]
  (go-loop []
    (when-let [aika (<! vanhojen-poisto-ajat)]
      (try
        (poista-vanhat-tyokonesijainnit db)
        (catch Throwable e nil))  ; ignoraa poikkeukset että periodinen timer pysyy aina päällä
      (recur))))

(defrecord TyokoneenseurantaPuhdistus []
  component/Lifecycle
  (start [this]
    (aloita-vanhojen-poisto (:db this))
    this)
  (stop [this]
    (pysayta-vanhojen-poisto)
    this))
