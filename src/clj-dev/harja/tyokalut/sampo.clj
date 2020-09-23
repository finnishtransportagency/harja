(ns harja.tyokalut.sampo
  "Työkalu SAMPO toimenpidekoodien importtaamiseksi Excelistä INSERT lauseiksi."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (org.apache.poi.hssf.usermodel HSSFSheet HSSFWorkbook))
  (:gen-class))

(defn lue-sampo-xls [tiedosto]
  (with-open [xls (io/input-stream (io/file tiedosto))]
    (HSSFWorkbook. xls)))


(defn sheetit [workbook]
  (loop [sheetit []
         i 0]
    (if (= i (.getNumberOfSheets workbook))
      sheetit
      (recur (conj sheetit (.getSheetAt workbook i)) (inc i)))))


(defn kaikki-koodit
  "Käy läpi koko XLS sheetin ja hakee kaikki SAMPO koodit ja niiden nimet"
  [sheet]
  (let [hae-solu (fn [s r]
                   (some-> sheet
                           (.getRow r)
                           (.getCell s)
                           str))]
    (loop [rivi 0
           sarake 0
           koodit []]

      (if (= rivi (.getLastRowNum sheet))
        (sort-by first koodit)
        (let [xls-rivi (.getRow sheet rivi)]
          (if (nil? xls-rivi)
            (recur (inc rivi) 0 koodit)

            (if (= sarake (dec (.getLastCellNum xls-rivi)))
              (recur (inc rivi) 0 koodit)
              ;; tarkistetaan onko tässä kohdassa sampo koodi
              (let [solu (hae-solu sarake rivi)]
                (if-let [sampo (some->> solu (re-seq #"^(\d{5,6})\.0$") first second)]
                  (recur rivi (inc sarake)
                         (conj koodit [sampo (hae-solu (inc sarake) rivi)]))
                  (recur rivi (inc sarake) koodit))))))))))


(defn koodi->sql [[koodi nimi]]
  (let [[emo taso] (cond
                     ;; Viimeinen on nolla => 2. tason koodi
                     (.endsWith koodi "0")
                     [(str (.substring koodi 0 2) "000") 2]

                     ;; Viimeinen ei ole nolla => 3. tason koodi
                     (not (.endsWith koodi "0"))
                     [(str (.substring koodi 0 (dec (count koodi))) "0") 3]

                     :default [nil 1])]
    (str "SELECT lisaa_toimenpidekoodi(
         '" (str/replace nimi #" *\(level \d\)" "") "',"    ;; nimi
         "'" koodi "',"                                     ;; koodi
         (if (= emo koodi) 1 taso)                          ;; taso
         ", "
         " null, "                                          ;; yksikko
         " null, "                                          ;; tuotenumero

         (if (and emo (not= emo koodi))
           (str " (SELECT nimi FROM toimenpidekoodi WHERE koodi='" emo "') ")
           " NULL ")                                        ;; emon nimi
         ", "

         (if (and emo (not= emo koodi))
           (str " (SELECT koodi FROM toimenpidekoodi WHERE koodi='" emo "') ")
           " NULL ")                                        ;; emon koodi
         ", "

         (if (and emo (not= emo koodi))
           (str " (SELECT taso FROM toimenpidekoodi WHERE koodi='" emo "') ")
           " NULL ")                                        ;; emon taso

         ");\n")))

(defn -main [& args]
  (assert (= 2 (count args))
          "Anna kaksi parametria: SAMPO tuotekoodit XLS ja tulos SQL tiedosto")
  (let [[sampo-xls tulos-sql] args]
    (->> sampo-xls
         lue-sampo-xls
         sheetit
         (drop 2) ;; 2 ekaa ei ole samanlaisia tuotelistoja
         (mapcat kaikki-koodit)
         (map koodi->sql)
         (reduce str)
         (spit tulos-sql))))

            
            
