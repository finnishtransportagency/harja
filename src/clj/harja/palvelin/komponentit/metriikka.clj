(ns harja.palvelin.komponentit.metriikka
  "Tarjoaa metriikkaa tämän palvelininstanssin terveydentilasta"
  (:require [com.stuartsierra.component :as component]
            [metrics.core :as metrics]
            [metrics.gauges :as gauges :refer [defgauge]]
            [metrics.reporters.jmx :as jmx]
            [taoensso.timbre :as log]))

(defprotocol Metriikka
  (lisaa-mittari! [this nimi mittari-fn]
    "Lisää numerotyyppinen mittari, jota pollataan. Mittari-fn on parametriton funktio, jonka
    tulee palauttaa arvo kutsuttaessa."))


(defrecord JmxMetriikka []
  component/Lifecycle
  (start [this]
    (let [registry (metrics/new-registry)
          reporter (jmx/reporter registry)]
      (jmx/start reporter)
      (assoc this
             ::registry registry
             ::reporter reporter)))

  (stop [{reporter ::reporter :as this}]
    (jmx/stop reporter)
    (dissoc this ::registry ::reporter))

  Metriikka
  (lisaa-mittari! [{reg ::registry} nimi mittari-fn]
    (gauges/gauge-fn reg nimi mittari-fn)))

(defn luo-jmx-metriikka []
  (->JmxMetriikka))
