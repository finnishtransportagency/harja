(ns harja.tiedot.hallintayksikot
  "Hallinnoi hallintayksiköiden tietoja"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]))

(def hallintayksikot (atom nil))

(defn ^:export haeppas []
  (k/request! :hallintayksikot
              :tie
              (fn [yksikot]
                (.log js/console "Yksiköt: " (pr-str yksikot))
                (reset! hallintayksikot yksikot))))
                 
 
