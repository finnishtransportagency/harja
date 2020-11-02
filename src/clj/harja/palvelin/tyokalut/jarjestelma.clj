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
    (let [kaikki-komponentit (keys system)
          graph (component/dependency-graph system kaikki-komponentit)
          all-keys-sorted (sort (dep/topo-comparator graph) kaikki-komponentit)]
      (loop [system system
             [component-key & component-keys] system-component-keys]
        (if (nil? component-key)
          system
          (let [uudelleen-kaynnistettavat-komponentit (drop-while #(not= % component-key) all-keys-sorted)
                sammutettu-jarjestelma (component/update-system-reverse system uudelleen-kaynnistettavat-komponentit component/stop)
                uudelleen-kaynnistetty-jarjestelma (component/update-system sammutettu-jarjestelma uudelleen-kaynnistettavat-komponentit component/start)]
            (recur uudelleen-kaynnistetty-jarjestelma
                   component-keys)))))))

(defn system-restart
  [system system-component-keys]
  {:pre [(set? system-component-keys)
         (every? (fn [component-key]
                   (satisfies? component/Lifecycle (get system component-key)))
                 system-component-keys)]
   :post [(instance? SystemMap %)]}
  (kp/restart system system-component-keys))

(defn kaikki-ok? [system]
  (let [kaikki-komponentit (keys system)]
    (loop [[komponentin-nimi & loput-komponentit] kaikki-komponentit
           jarjestelma-ok? true]
      (if (or (nil? komponentin-nimi)
              (false? jarjestelma-ok?))
        jarjestelma-ok?
        (recur loput-komponentit
               (let [komponentti (get system komponentin-nimi)]
                 (or (not (satisfies? kp/IStatus komponentti))
                     (kp/status-ok? komponentti))))))))