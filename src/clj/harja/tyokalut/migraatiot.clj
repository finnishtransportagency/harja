(ns harja.tyokalut.migraatiot
  (require [clojure.string :as str])
  (:gen-class))

(defn tarkista-duplikaattinumerot []
  (->> "tietokanta/src/main/resources/db/migration"
       (java.io.File.)
       .listFiles
       (map #(.getName %))
       (map #(re-find #"V1_(\d+)__(.*).sql" %))
       (map #(drop 1 %))
       (group-by first)
       (some (fn [[num filet]]
               (when (and num (> (count filet) 1))
                 (str num ": " (str/join ", " (map second filet))))))))

(defn -main [& args]
  (let [dup (tarkista-duplikaattinumerot)]
    (if dup
      (println "DUPLIKAATTI: " dup)
      (println "Ei duplikaatteja."))))
