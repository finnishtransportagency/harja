(ns harja-laadunseuranta.testutils
  (:require [dommy.core :as dommy]))

(def *test-container* (cljs.core/atom nil))

(defn sel [path]
  (dommy/sel @*test-container* path))

(defn sel1 [path]
  (dommy/sel1 @*test-container* path))
