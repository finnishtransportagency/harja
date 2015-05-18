(ns harja.tiedot.urakka.maksuerat
  "Tämä nimiavaruus hallinnoi urakan maksueria."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-maksuerat [urakka-id]
    (k/post! :hae-urakan-maksuerat urakka-id))

(defn laheta-maksuerat [maksueranumerot]
    (k/post! :laheta-maksuerat-sampoon (into [] maksueranumerot))) ; TODO Rajapinta voisi ottaa vastaan setin eikä mappia?