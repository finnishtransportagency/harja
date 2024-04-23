(ns harja.palvelin.integraatiot.api.tyokalut.apurit)

(defn muuta-mapin-avaimet-keywordeiksi
  "Palauttaa mapin, jossa avaimet ovat keywordeja"
  [map]
  (reduce (fn [eka toka]
            (assoc
              eka
              (keyword toka)
              (get map toka)))
          {}
          (keys map)))

(defn- bytes->hex-string
  "Muodostetaan bytes arvosta string."
  [bytes]
  (reduce str (map #(format "%02x" (bit-and 0xFF %)) bytes)))

;; Harjaan lähetetään valtavia toteumia, joiden käsittely vie kauemmin, kuin mitä vaylapilvi timeout sallii.
;; Tehdään varmistus, että samaa toteumaa ei lähetetä uudestaan.
(defn md5-hash [string]
  (let [bytes (.getBytes string)]
    (-> (java.security.MessageDigest/getInstance "MD5")
      (.digest bytes)
      (bytes->hex-string))))
