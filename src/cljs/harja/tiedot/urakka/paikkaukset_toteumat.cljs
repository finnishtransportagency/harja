(ns harja.tiedot.urakka.paikkaukset-toteumat
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]))

(def app (atom {:paikkauksien-haku-kaynnissa? false
                :valinnat {:tr nil}}))

;; Muokkaukset
(defrecord ValinnatTrMuokattu [tr])

(extend-protocol tuck/Event
  ValinnatTrMuokattu
  (process-event [{tr :tr} app]
    (assoc-in app [:valinnat :tr] tr)))