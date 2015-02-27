(ns harja.tiedot.indeksit
  "Indeksien tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def indeksit (atom nil))

 (defn hae-indeksit []
   (if (empty? @indeksit)
           (go (reset! indeksit
               (<! (k/get! :indeksit))))))
 
 
(defn tallenna-indeksit
  "Tallentaa indeksit, palauttaa kanavan, josta vastauksen voi lukea."
  [indeksit poistettavat]
  (log "TALLENNA indeksit: " (pr-str indeksit) " \n JA POISTETAAN: " (pr-str poistettavat))
  (k/post! :tallenna-indeksit
           {:indeksit indeksit
            :poistettu poistettavat}))
