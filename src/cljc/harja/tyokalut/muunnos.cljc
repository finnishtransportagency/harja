(ns harja.tyokalut.muunnos)

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
