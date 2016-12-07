(ns harja.palvelin.tyokalut.svg
  (:require
    [taoensso.timbre :as log]
            [clojure.string :as str])
  (:import (hiccup.compiler HtmlRenderer)))

(defn create-inline-svg [path]
  (let [svg (slurp path)]
    [:div {:style "display: none;"}
     (reify HtmlRenderer
       (render-html [_] svg))]))

(defmacro inline-svg [path]
  `(let [svg# (create-inline-svg ~path)]
     svg#))
