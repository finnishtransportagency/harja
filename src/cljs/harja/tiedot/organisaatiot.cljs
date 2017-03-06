(ns harja.tiedot.organisaatiot
  "Harjan organisaatioden tietojen hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [tarkkaile!]]
            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def organisaatiot "Organisaatiot" (atom #{}))

(defn ^:export hae-organisaatiot []
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :hae-organisaatiot nil))]
        (>! ch res))
      (close! ch))
    ch))

(t/kuuntele! :harja-ladattu (fn [_]
                              (go (reset! organisaatiot (<! (k/post! :hae-organisaatiot nil))))))
