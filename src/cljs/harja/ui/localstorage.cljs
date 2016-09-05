(ns harja.ui.localstorage
  (:require [harja.loki :refer [log logt tarkkaile!]]))

(defn- boolean-teksti->boolean
  "Muuntaa tekstinä tallennetun totuusarvon boolean-arvoksi."
  [teksti]
  (= teksti "true"))

(defn- boolean->boolean-teksti
  "Tallentaa totuusarvon tekstinä 'true' tai 'false'."
  [totuus]
  (if (boolean totuus)
    "true"
    "false"))

(defn tallenna-tekstiarvo
  "Tallentaa tekstin avaimen taakse."
  [avain teksti]
  (assert avain "Arvoa ei voi asettaa localstorageen ilman avainta")
  (.setItem js/localStorage avain teksti))

(defn lue-tekstiarvo
  "Palauttaa tallennetun arvon avaimen takaa tai nil jos avainta ei ole."
  [avain]
  (assert avain "Ei voida hakea localstoragesta ilman avainta")
  (.getItem js/localStorage avain))

(defn tallenna-totuusarvo
  "Tallentaa totuusarvon tekstimuodossa avaimen taakse."
  [avain totuus]
  (log "Tallenna totuusarvo localstorageen: " (pr-str totuus))
  (assert avain "Arvoa ei voi asettaa localstorageen ilman avainta")
  (.setItem js/localStorage avain (boolean->boolean-teksti totuus)))

(defn lue-totuusarvo
  "Lukee tallennetun totuusarvon avaimen takaa palauttaen true tai false.
   Jos avainta ei ole, palauttaa nil."
  [avain]
  (log "Lue totuusarvo localstoragesta: " (pr-str avain))
  (assert avain "Ei voida hakea localstoragesta ilman avainta")
  (when-let [arvo (.getItem js/localStorage avain)]
    (boolean-teksti->boolean arvo)))