(ns harja.atom
  (:require [cljs.core.async :refer [put!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

;; Tallennetaan reaktioiden kanavat, komentamista varten
(defonce +reaktiot+ (atom {}))

(defn paivita!
  "Pakottaa reaktion päivittymään."
  [reaktio<!]
  (when-let [paivita (:paivita (get @+reaktiot+ reaktio<!))]
    (paivita)))
  

  
  
