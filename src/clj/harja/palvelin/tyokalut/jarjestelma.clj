(ns harja.palvelin.tyokalut.jarjestelma
  (:require [com.stuartsierra.component :as component]
            [com.stuartsierra.dependency :as dep]
            [clojure.core.async :as async]
            [harja.palvelin.tyokalut.komponentti-protokollat :as kp])
  (:import (com.stuartsierra.component SystemMap)))

(defn- restart-komp [komp]
  (component/stop komp)
  (component/start komp))

(defonce ^:private uudelleen-kaynnistajan-lukko (Object.))

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
  (locking uudelleen-kaynnistajan-lukko
    (kp/restart system system-component-keys)))

(defn kaikki-ok?
  "Tarkistaa, että onhan systeemi ok niiltä osin kuin IStatus protokolla on määritelty.
   Lisäksi voidaan antaa timeout (millisekuntteja), jonka ajan testejä maksimissaan ajellaan."
  ([system] (kaikki-ok? system nil))
  ([system timeout]
   {:pre [(instance? SystemMap system)
          (or (nil? timeout)
              (int? timeout))]
    :post [(boolean? %)]}
   (let [max-ts (+ timeout (System/currentTimeMillis))
         kaikki-komponentit (keys system)
         jarjestelma-test (fn []
                            (loop [[komponentin-nimi & loput-komponentit] kaikki-komponentit
                                   jarjestelma-ok? true]
                              (if (or (nil? komponentin-nimi)
                                      (false? jarjestelma-ok?))
                                jarjestelma-ok?
                                (recur loput-komponentit
                                       (let [komponentti (get system komponentin-nimi)]
                                         (or (not (satisfies? kp/IStatus komponentti))
                                             (kp/status-ok? komponentti)))))))]
     (if timeout
       (async/<!! (async/go-loop [ok? (jarjestelma-test)]
                    (if (or ok?
                            (> (System/currentTimeMillis) max-ts))
                      ok?
                      (do (async/<! (async/timeout 500))
                          (recur (jarjestelma-test))))))
       (jarjestelma-test)))))