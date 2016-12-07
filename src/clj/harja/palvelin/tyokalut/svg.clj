(ns harja.palvelin.tyokalut.svg
  (:require
    [taoensso.timbre :as log]
            [clojure.string :as str])
  (:import (hiccup.compiler HtmlRenderer)))

(defn- list-svgs [path]
  (let [files (file-seq (clojure.java.io/file path))
        files (map #(-> {:name (.getName %)
                         :path (.getAbsolutePath %)})
                   files)
        svg-files (filter #(str/ends-with? (:name %) ".svg") files)
        svg-files (map #(assoc % :contents (slurp (:path %))) svg-files)]
    (into [] svg-files)))

(defn create-inline-svg [path]
  (let [svgs (list-svgs path)]
    (apply conj [:div {:style "display: none;"}]
           (into [] (map #(reify HtmlRenderer
                            (render-html [_] (:contents %)))
                         svgs)))))

(defmacro inline-svg [path]
  `(let [svg# (create-inline-svg ~path)]
     svg#))
