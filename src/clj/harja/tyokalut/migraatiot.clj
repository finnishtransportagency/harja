(ns harja.tyokalut.migraatiot
  (:require [clojure.string :as str]
            [clojure.java.shell :as sh])
  
  (:gen-class))

(def migraatiohakemisto "tietokanta/src/main/resources/db/migration")

(defn migraatiotiedostot []
  (->> migraatiohakemisto
       (java.io.File.)
       .listFiles
       (map #(.getName %))
       (filter #(and (not= % "V1__Perustiedot.sql")
                     (.startsWith % "V1_")
                     (.endsWith % ".sql")))))

(defn numero-ja-kuvaus [tiedostonimi]
  (let [[_ num kuvaus] (re-find #"V1_(\d+)__(.*).sql" tiedostonimi)
        kuvaus (if (str/blank? kuvaus)
                 (first (str/split (slurp (str migraatiohakemisto "/" tiedostonimi)) #"\n"))
                 kuvaus)]
    [(Integer/parseInt num) kuvaus]))

(defn migraatiot []
  (as-> (migraatiotiedostot) m
    (map numero-ja-kuvaus m)
    (zipmap (map first m)
            (map second m))))

(defn nykyinen-branch []
  (as-> (:out (sh/sh "git" "branch")) res
    (str/split res #"\n")
    (filter #(.startsWith % "* ") res)
    (first res)
    (.substring res 2)))

(defn vaihda-branch [b]
  (sh/sh "git" "checkout" b)
  (assert (= (nykyinen-branch) b) "Branching vaihto ei onnistunut"))

(defn pull []
  (sh/sh "git" "pull"))

(defn -main [& args]
  (let [nykyinen-branch (nykyinen-branch)]
    (if (= "develop" nykyinen-branch)
      (println "Olet jo developissa!")
      (let [branchin-migraatiot (migraatiot)]
        (vaihda-branch "develop")
        (pull)
        (let [developin-migraatiot (migraatiot)]
          (println "Korkein migraatio tässä branchissa: " (reduce max (keys branchin-migraatiot)))
          (println "Korkein migraatio develop branchissa: " (reduce max (keys developin-migraatiot)))
          (doseq [[num branch-kuvaus] branchin-migraatiot
                  :let [develop-kuvaus (get developin-migraatiot num)]]
            (when (not= branch-kuvaus develop-kuvaus)
              (println "Migraatio " num " kuvaus eroaa tämän ja developin välillä!\n"
                       "  Tässä haarassa:" branch-kuvaus "\n"
                       "  Developissa:   " develop-kuvaus))))
        (vaihda-branch nykyinen-branch)))
    (System/exit 0)))
