(ns harja.domain.puhelinnumero
  (:require [clojure.string :as string]))

(defn kanonisoi
  "Muuttaa puhelinnumeron kanoniseen (kansainväliseen) muotoon"
  [puhelinnumero]
  (when (not (string/blank? puhelinnumero))
    (let [puhelinnumero
          (-> puhelinnumero
              (clojure.string/replace #"([)(-]|[a-z]|[A-Z])" "")
              (clojure.string/replace " " ""))]
      (if (= "+" (.substring puhelinnumero 0 1))
        puhelinnumero
        (str "+358" (.substring puhelinnumero 1))))))
