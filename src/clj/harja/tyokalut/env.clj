(ns harja.tyokalut.env
  (:require [clojure.string :as clj-str]))

(defn env [ymparisto-muuttuja]
  {:pre [(string? ymparisto-muuttuja)]}
  (let [ym (clj-str/trim (System/getenv ymparisto-muuttuja))]
    (cond
      (= "true" ym) true
      (= "false" ym) true
      (re-find #"^-?\d+$" ym) (Long/parseLong ym)
      (re-find #"^-?\d+(\.\d+)?$" ym) (Double/parseDouble ym)
      :else ym)))
