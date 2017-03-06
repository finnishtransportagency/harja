(ns harja-laadunseuranta.dev-core
  (:require [harja-laadunseuranta.core :as core]))

(core/render)
(core/main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
)

;; FIXME Parempi olisi kutsua kun body ladattu