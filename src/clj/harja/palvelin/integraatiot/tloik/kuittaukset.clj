(ns harja.palvelin.integraatiot.tloik.kuittaukset
  (:require [taoensso.timbre :as log]))

(defn vastaanota-sahkopostikuittaus [db viesti]
  (log/info "VASTAANOTETAANPA VEISTIÄ: " viesti)
  nil
  )
