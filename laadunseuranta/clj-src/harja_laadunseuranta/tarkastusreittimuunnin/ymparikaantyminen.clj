(ns harja-laadunseuranta.tarkastusreittimuunnin.ymparikaantyminen
  (:require [taoensso.timbre :as log]
            [harja.domain.tierekisteri :as tr-domain]))

(defn lisaa-tieto-ymparikaantymisesta
  [merkinnat]
  ;; TODO Analysoi merkinnät ja lisää merkintään avain ":ymparikaantyminen? true" jos siinä käännytään ympäri
  ;; Ks. HAR-4007
  merkinnat)