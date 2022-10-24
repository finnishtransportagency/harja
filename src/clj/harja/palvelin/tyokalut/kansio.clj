(ns harja.palvelin.tyokalut.kansio
  (:require [clojure.java.io :as io]))

(defn poista-tiedostot [polku]
  (let [kansio (clojure.java.io/file polku)
        tiedostot (.listFiles kansio)]
    (doseq [tiedosto tiedostot]
      (when-not (or
                 (= ".gitkeep" (.getName tiedosto))
                 (.isDirectory tiedosto))
        (io/delete-file tiedosto)))))

(defn luo-jos-ei-olemassa [polku]
  (let [kansio (clojure.java.io/file polku)]
    (when (not (.exists kansio))
      (.mkdirs kansio))))

(defn onko-tiedosto-olemassa? [polku]
      (.exists (clojure.java.io/file polku)))