(ns harja.tyokalut.migraatiot
  (require [clojure.string :as str])
  (:gen-class))

(defn migraatiotiedostot []
  (->> "tietokanta/src/main/resources/db/migration"
       (java.io.File.)
       .listFiles
       (map #(.getName %))))

(defn tarkista-duplikaattinumerot []
  (->> (migraatiotiedostot)
       (map #(re-find #"V1_(\d+)__(.*).sql" %))
       (map #(drop 1 %))
       (group-by first)
       (some (fn [[num filet]]
               (when (and num (> (count filet) 1))
                 (str num ": " (str/join ", " (map second filet))))))))

(defn tarkista-skandit []
  (->> (migraatiotiedostot)
       (keep #(when (some {\ä \Ä \ö \Ö} %) %))))

                    
(defn -main [& args]
  (let [dup (tarkista-duplikaattinumerot)
        skandit (tarkista-skandit)]
    (if dup
      (println "DUPLIKAATTI: " dup)
      (println "Ei duplikaatteja."))
    (if-not (empty? skandit)
      (println "Skandeja nimessä: " (str/join ", " skandit))
      (println "Ei skandeja."))))
