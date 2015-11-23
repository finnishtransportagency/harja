(ns harja.palvelin.tyokalut.kansio
  (:require [clojure.java.io :as io]))

(defn poista-tiedostot [polku]
  (let [kansio (clojure.java.io/file polku)
        tiedostot (.listFiles kansio)]
    (doseq [tiedosto tiedostot]
      (when-not (= ".gitkeep" (.getName tiedosto)) (io/delete-file tiedosto)))))

(defn luo-jos-ei-olemassa [polku]
  (let [kansio (clojure.java.io/file polku)]
    (when (not (.exists kansio))
      (.mkdirs kansio))))