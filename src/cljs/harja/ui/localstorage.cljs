(ns harja.ui.localstorage
  (:require [harja.loki :refer [log logt tarkkaile!]]))

(defn aseta-arvo [avain arvo]
  (assert avain "Arvoa ei voi asettaa localstorageen ilman avainta")
  (.setItem js/localStorage avain arvo))

(defn lue-arvo [avain]
  (assert avain "Ei voida hakea localstoragesta ilman avainta")
  (.getItem js/localStorage avain))