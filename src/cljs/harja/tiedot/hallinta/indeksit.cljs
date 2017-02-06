(ns harja.tiedot.hallinta.indeksit
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

(defonce indeksien-nimet
  (let [a (atom nil)]
    (go (reset! a (<! (k/get! :indeksien-nimet))))
    a))

(defn tallenna-indeksi
  "Tallentaa indeksiarvot, palauttaa kanavan, josta vastauksen voi lukea."
  [nimi uudet-indeksivuodet]
  (go (let [tallennettavat
            (into []
                  (comp (filter #(not (:poistettu %))))
                  uudet-indeksivuodet)
            res (<! (k/post! :tallenna-indeksi
                             {:nimi nimi
                              :indeksit tallennettavat}))]
        (reset! indeksit res)
        true)))

(defonce urakkatyypin-indeksit
  (let [a (atom nil)]
    (go (reset! a (<! (k/get! :urakkatyypin-indeksit))))
    a))