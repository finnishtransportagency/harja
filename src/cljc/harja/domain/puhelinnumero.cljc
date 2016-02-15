(ns harja.domain.puhelinnumero)

(defn kanonisoi
  "Muuttaa puhelinnumeron kanoniseen (kansainväliseen) muotoon"
  [puhelinnumero]
  (let [puhelinnumero
        (-> puhelinnumero
            (clojure.string/replace #"([)(-]|[a-z]|[A-Z])" "")
            (clojure.string/replace " " ""))]
    (if (= "+" (.substring puhelinnumero 0 1))
      puhelinnumero
      (str "+358" (.substring puhelinnumero 1)))))