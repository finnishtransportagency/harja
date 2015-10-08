(ns harja.palvelin.tyokalut.kansio
  (:require [clojure.java.io :as io]))

(defn poista-tiedostot [kansio]
  (let [kansio (clojure.java.io/file kansio)
        tiedostot (.listFiles kansio)]
    (doseq [tiedosto tiedostot]
      (io/delete-file tiedosto))))