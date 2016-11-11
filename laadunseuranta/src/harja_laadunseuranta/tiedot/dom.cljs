(ns harja-laadunseuranta.tiedot.dom
  (:require [cljs.core.async :as async :refer [<! >! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.tiedot.indexeddb-macros :refer [with-transaction with-objectstore with-cursor]]))


(defn korkeus [] (-> js/window .-innerHeight))
(defn leveys [] (-> js/window .-innerWidth))