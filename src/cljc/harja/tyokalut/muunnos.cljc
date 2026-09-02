(ns harja.tyokalut.muunnos
  (:require [clojure.string :as str]))

(defn ms->s
  "Muuttaa millisekunnit sekunneiksi"
  [ms]
  {:pre [(integer? ms)]
   :post [(integer? %)]}
  (let [arvo (/ ms 1000)]
    (if (integer? arvo)
      arvo
      #?(:clj (Math/round arvo)
         :cljs (js/Math.round arvo)))))

#?(:clj
   (defn str->double [s]
     (when (and s (not (str/blank? s)))
       (Double/parseDouble s))))

(defn keywordiksi
  "Muuntaa arvon keywordiksi, säilyttäen nil sellaisenaan."
  [arvo]
  (when arvo (keyword arvo)))

(defn keyword->bool
  "palauttaa alkuperäisen arvon jos ei ole :true tai :false"
  [x]
  (case x
    :true  true
    :false false
    x))
