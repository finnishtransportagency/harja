(ns harja.tyokalut.env
  (:require [clojure.string :as clj-str]
            [cheshire.core :as cheshire]))

(defn env
  ([ymparisto-muuttuja] (env ymparisto-muuttuja nil))
  ([ymparisto-muuttuja default-arvo] (env ymparisto-muuttuja default-arvo false))
  ([ymparisto-muuttuja default-arvo json?]
   {:pre [(string? ymparisto-muuttuja)]}
   (let [ym (clj-str/trim (str (System/getenv ymparisto-muuttuja)))]
     (cond
       (and (= "" ym)
            (some? default-arvo)) default-arvo
       (= "" ym) nil
       json? (cheshire/parse-string ym true)
       (= "true" (clj-str/lower-case ym)) true
       (= "false" (clj-str/lower-case ym)) false
       (re-find #"^-?\d+$" ym) (Long/parseLong ym)
       (re-find #"^-?\d+(\.\d+)?$" ym) (Double/parseDouble ym)
       :default ym))))
