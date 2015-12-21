(ns harja.ui.skeema)

(defn laske-sarakkeiden-leveys [skeema]
  (if (every? number? (map :leveys skeema))
    ;; Jos kaikki leveydet ovat numeroita (ei siis prosentti stringejä),
    ;; voidaan niille laskea suhteelliset leveydet
    (let [yhteensa (reduce + (map :leveys skeema))]
      (mapv (fn [{lev :leveys :as kentta}]
              (let [#?@(:cljs [parsi-luku-fn #(.toFixed % 1)]
                        :clj [parsi-luku-fn #(Double/toString %)])]
                (assoc kentta
                  :leveys (str
                            (parsi-luku-fn
                              (* 100.0 (/ lev yhteensa))) "%"))))
            skeema))
    skeema))
