(ns harja.tyokalut.local-storage
  "Storage-atom wrapper, joka asettaa harjan käyttämät transit käsittelijät"
  (:require [harja.transit :as t]
            [alandipert.storage-atom :as st]
            [reagent.core :as r]))

(swap! st/transit-read-handlers merge (:handlers t/read-optiot))
(swap! st/transit-write-handlers merge (:handlers t/write-optiot))
(reset! st/storage-delay 500)

(defn local-storage-atom [nimi alkuarvo]
  (st/local-storage
   (r/atom alkuarvo)
   nimi))
