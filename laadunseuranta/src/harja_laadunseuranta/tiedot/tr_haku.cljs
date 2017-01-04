(ns harja-laadunseuranta.tiedot.tr-haku
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [harja-laadunseuranta.macros :refer [with-delay-loop]]
                   [cljs.core.async.macros :refer [go-loop]]
                   [reagent.ratom :refer [run!]]))

(defn alusta-tr-haku [sijainti-atomi tr-tiedot]
  (go-loop [pos (:nykyinen @sijainti-atomi)]
    (let [result (:ok (<! (comms/hae-tr-tiedot pos)))]
      (reset! tr-tiedot result)
      (<! (timeout 2000))
      (recur (:nykyinen @sijainti-atomi)))))