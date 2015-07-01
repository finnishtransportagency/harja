(ns harja.atom
  "Erinäisiä atomien ja tilan käsittelyn apureita"
  (:require [cljs.core.async :refer [<! >! chan put! alts! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

;; Tallennetaan reaktioiden kanavat, komentamista varten
(defonce +reaktiot+ (atom {}))

(defn paivita!
  "Pakottaa reaktion päivittymään."
  [reaktio<!]
  (when-let [paivita (:paivita (get @+reaktiot+ reaktio<!))]
    (paivita)))
  


(defn kuristin
  "Palauttaa funktion, joka ottaa samat parametrit kuin annettu paivitys-fn, mutta
   ei suorita funktiota heti, vaan odottaa jos parametrit muuttuvat uudelleen.
   Oletuksena odotusaika on 100ms.

   Päivitysfunktion odotetaan tekevän jotain sivuvaikutuksia (kuton atomin reset),
   eikä sen paluuarvolla tehdä mitään."
  ([paivitys-fn] (kuristin 100 paivitys-fn))
  ([odotusaika paivitys-fn]
   (let [parametrit-ch (chan)]
     (go (loop [parametrit (<! parametrit-ch)]
           (let [[arvo kanava] (alts! [parametrit-ch (timeout odotusaika)])]
             (if (= kanava parametrit-ch)
               ;; Uudet parametrit tuli ennen timeouttia
               (recur arvo)
               
               ;; timeout
               (do (apply paivitys-fn parametrit)
                   (recur (<! parametrit-ch)))))))
     (fn [& parametrit]
       (put! parametrit-ch parametrit)))))
  
