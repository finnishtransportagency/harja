(ns harja.tiedot.urakka.maksuerat
  "Tämä nimiavaruus hallinnoi urakan maksueria."
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn hae-urakan-maksuerat [urakka-id]
  (k/post! :hae-urakan-maksuerat urakka-id))

(defn laheta-maksuerat [maksueranumerot urakka-id]
  (k/post! :laheta-maksuerat-sampoon {:maksueranumerot (into [] maksueranumerot)
                                      :urakka-id urakka-id}))


(defonce nakymassa? (atom false))

(defonce maksuerat
  (reaction<! [urakan-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and urakan-id nakymassa?)
                (hae-urakan-maksuerat urakan-id))))
