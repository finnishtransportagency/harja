(ns harja.palvelin.tyokalut.jarjestelma
  (:require [com.stuartsierra.component :as component]
            [com.stuartsierra.dependency :as dep]
            [harja.palvelin.tyokalut.komponentti-protokollat :as kp])
  (:import (com.stuartsierra.component SystemMap)))

(defn- restart-komp [komp]
  (component/stop komp)
  (component/start komp))

(extend-type SystemMap
  kp/IRestart
  (restart [system system-component-keys]
    (let [all-component-keys (keys system)
          graph (component/dependency-graph system all-component-keys)
          all-keys-sorted (sort (dep/topo-comparator graph) all-component-keys)]
      (loop [system system
             [component-key & component-keys] system-component-keys]
        (if (nil? component-key)
          system
          (let [[uudelleen-kaynnistettava-komponentti-avain & paivitettavat-komponentti-avaimet :as pk] (drop-while #(not= % component-key) all-keys-sorted)]
            (let [osittain-pysaytetty-systeemi (component/stop-system system pk)
                  komponentti-uudelleen-kaynnistetty (component/update-system osittain-pysaytetty-systeemi uudelleen-kaynnistettava-komponentti-avain restart-komp)]
              (recur (component/update-system komponentti-uudelleen-kaynnistetty paivitettavat-komponentti-avaimet component/start)
                     component-keys))))))))

(defn system-restart
  [system system-component-keys]
  {:pre [(set? system-component-keys)
         (every? (fn [component-key]
                   (satisfies? component/Lifecycle (get system component-key)))
                 system-component-keys)]
   :post [(instance? SystemMap %)]}
  (kp/restart system system-component-keys))