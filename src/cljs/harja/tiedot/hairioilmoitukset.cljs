(ns harja.tiedot.hairioilmoitukset
  (:require [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.loki :refer [log tarkkaile!]]

            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.modal :as modal]
            [goog.dom :as dom]
            [goog.events :as events]
            [reagent.core :as reagent]
            [goog.net.cookies :as cookie]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(def tarkkailyvali-ms 60000)
(def tuore-hairioilmoitus (atom nil))

(defn hae-tuorein-hairioilmoitus []
  (go (let [vastaus (<! (k/post! :hae-tuorein-voimassaoleva-hairioilmoitus {}))]
        (when-not (k/virhe? vastaus)
          (reset! tuore-hairioilmoitus vastaus)))))

(defn tarkkaile-hairioilmoituksia []
  (hae-tuorein-hairioilmoitus)
  (go-loop []
    (<! (timeout tarkkailyvali-ms))
    (hae-tuorein-hairioilmoitus)
    (recur)))