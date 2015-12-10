(ns harja.tiedot.hallintayksikot
  "Hallinnoi hallintayksiköiden tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t])
  
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def hallintayksikot (atom nil))
 
(go (reset! hallintayksikot
            (into []
                  (map #(assoc % :type :hy))
                  (<! (k/post! :hallintayksikot
                               ;; FIXME: tämä päätellään serverin puolella käyttäjästä, kun on sen aika
                               :tie)))))
