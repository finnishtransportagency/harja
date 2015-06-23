(ns harja.tiedot.indeksit
  "Indeksien tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(def indeksit (atom nil))

 (defn hae-indeksit []
   (if (nil? @indeksit)
           (go (reset! indeksit
               (<! (k/get! :indeksit))))))
 
 
(defn tallenna-indeksi
  "Tallentaa indeksit, palauttaa kanavan, josta vastauksen voi lukea."
  [nimi indeksit]
  (k/post! :tallenna-indeksi
           {:nimi nimi
            :indeksit indeksit}))

(defonce indeksien-nimet
  (let [a (atom nil)]
    (go (reset! a (<! (k/get! :indeksien-nimet))))
    a))
