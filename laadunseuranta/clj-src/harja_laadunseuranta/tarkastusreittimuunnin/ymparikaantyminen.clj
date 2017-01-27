(ns harja-laadunseuranta.tarkastusreittimuunnin.ymparikaantyminen
  (:require [taoensso.timbre :as log]
            [harja.domain.tierekisteri :as tr-domain]))

(defn lisaa-tieto-ymparikaantymisesta
  [merkinnat]
  (doseq [merkinta merkinnat]
    (log/debug merkinta))
  merkinnat)