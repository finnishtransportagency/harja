(ns harja.palvelin.komponentit.metriikka
  "Tarjoaa metriikkaa tämän palvelininstanssin terveydentilasta"
  (:require [metrics.core :as metrics]
            [metrics.gauges :as gauges :refer [defgauge]]
            [metrics.reporters.jmx :as jmx]
            [taoensso.timbre :as log]))

(defonce metriikat (metrics/new-registry))

(defgauge metriikat paljonko-kello-on
  (fn []
    (System/currentTimeMillis)))

(defonce JR (jmx/reporter {}))

(defn start []
  (log/info "JMX metriikoiden raportointi aloitettu")
  (jmx/start JR))
