(ns harja-laadunseuranta.tiedot.dom
  (:require
    [harja-laadunseuranta.asiakas.tapahtumat :as tapahtumat]
    [reagent.core :as reagent :refer [atom]]
    [cljs.core.async :as async :refer [<! >! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.tiedot.indexeddb-macros :refer [with-transaction with-objectstore with-cursor]]))


(def leveys (atom (-> js/window .-innerWidth)))
(def korkeus (atom (-> js/window .-innerHeight)))

(defn- paivita-leveys! []
  (reset! leveys (-> js/window .-innerWidth)))

(defn- paivita-korkeus! []
  (reset! korkeus (-> js/window .-innerHeight)))

(defn kuuntele-leveyksia []
  (.addEventListener js/window "resize" #(do (paivita-leveys!)
                                             (paivita-korkeus!)
                                             (tapahtumat/julkaise! {:aihe :window-resize}))))

(kuuntele-leveyksia)