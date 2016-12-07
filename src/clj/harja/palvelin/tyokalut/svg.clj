(ns harja.palvelin.tyokalut.svg
  (:require [pl.danieljanus.tagsoup :refer :all]
            [taoensso.timbre :as log]
            [clojure.string :as str]))

(defn- list-svgs [path]
  (let [files (file-seq (clojure.java.io/file path))
        files (map #(-> {:name (.getName %)
                         :path (.getAbsolutePath %)})
                   files)
        svg-files (filter #(str/ends-with? (:name %) ".svg") files)
        svg-files (map #(assoc % :contents (slurp (:path %))) svg-files)]
    (into [] svg-files)))

(defn- svg-to-hiccup [file]
  (assoc file :contents (parse-string (:contents file))))

(defn- svgs-to-hiccup [files]
  (map svg-to-hiccup files))

(defn get-svgs [path]
  (svgs-to-hiccup (list-svgs path)))

(defn create-inline-svg [path]
  (let [svgs (get-svgs path)]
    (apply conj [:div {:style "display: none;"}]
           (into [] (map :contents svgs)))))

(defmacro inline-svg [path]
  `(let [svg# (create-inline-svg ~path)]
     svg#))
