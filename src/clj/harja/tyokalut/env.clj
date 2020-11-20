(ns harja.tyokalut.env
  (:require [clojure.string :as clj-str]))

(defn env
  ([ymparisto-muuttuja] (env ymparisto-muuttuja nil))
  ([ymparisto-muuttuja default-arvo]
   {:pre [(string? ymparisto-muuttuja)]}
   (let [ym (clj-str/trim (str (System/getenv ymparisto-muuttuja)))]
     (cond
       (and (= "" ym)
            (some? default-arvo)) default-arvo
       (= "" ym) nil
       (= "true" (clj-str/lower-case ym)) true
       (= "false" (clj-str/lower-case ym)) false
       (re-find #"^-?\d+$" ym) (Long/parseLong ym)
       (re-find #"^-?\d+(\.\d+)?$" ym) (Double/parseDouble ym)
       :else ym))))
