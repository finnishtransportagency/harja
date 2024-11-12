(ns harja.palvelin.integraatiot.api.tyokalut.apurit
  (:require [harja.kyselyt.konversio :as konv]))

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

(defn requestin-versionumero [request]
  (konv/konvertoi->int (get-in request [:params "api_version"])))
