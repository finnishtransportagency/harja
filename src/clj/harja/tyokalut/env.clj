(ns harja.tyokalut.env
  (:require [clojure.string :as clj-str]
            [cheshire.core :as cheshire]))

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
       :default ym))))

(defn env-json
  ([ymparisto-muuttuja] (env-json ymparisto-muuttuja nil))
  ([ymparisto-muuttuja default-arvo] (env-json ymparisto-muuttuja default-arvo nil))
  ([ymparisto-muuttuja default-arvo key-fn]
   {:pre [(string? ymparisto-muuttuja)]}
   (let [ym (clj-str/trim (str (System/getenv ymparisto-muuttuja)))]
     (if
       (and (= "" ym)
         (some? default-arvo)) default-arvo
       (cheshire/decode ym key-fn)))))
