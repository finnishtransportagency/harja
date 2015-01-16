(ns harja.tiedot.urakat
  "Harjan urakkalistausten tietojen hallinta"
  (:require [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [chan <! >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def +urakkalistat+
  "Pitää muistissa urakkalistat hallintayksikön id:llä"
  (atom {}))

(comment 
(defn hallintayksikon-urakat
  "Palauttaa atomin, jossa on halutun hallintayksikön urakkalistat. Atomi palautetaan välittömästi 
ja sen arvo on nil jos tietoja ei ole vielä haettu palvelimelta."
  [hallintayksikko]
  (let [id (:id hallintayksikko)
        
        ;; Varmistetaan, että hallintayksikölle on atomi olemassa
        lista (get (swap! +urakkalistat+
                          (fn [ul]
                            (if (get ul id)
                              ul
                              (assoc ul id (atom nil))))) id)]
    
    ;; Lähdetään taustalla hakemaan listan sisältöä
    (go
      (let [urakat (<! (k/post! :hallintayksikon-urakat id))]
        (reset! lista (mapv (fn [ur]
                              (assoc ur :type :ur)) urakat))))
    ;; palautetaan lista välittömästi
    lista))
)

(defn hae-hallintayksikon-urakat [hallintayksikko]
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :hallintayksikon-urakat (:id hallintayksikko)))]
        (>! ch (mapv (fn [ur]
                       (assoc ur :type :ur))
                     res))))
    ch))
              
