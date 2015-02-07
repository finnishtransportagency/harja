(ns harja.tyokalut.varusteteema
  "Tierekisterin varusteteeman XLS määrittelyn muuntaminen johonkin parempaan skeemamuotoon"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.json :as json])
  (:import (org.apache.poi.hssf.usermodel HSSFSheet HSSFWorkbook)
           (org.apache.poi.xssf.usermodel XSSFWorkbook))
  (:gen-class))

(defn lue-xls [tiedosto]
  (with-open [xls (io/input-stream tiedosto)]
    (XSSFWorkbook. xls)))


(defn sheetit [workbook]
  (loop [sheetit {}
         i 0]
    (if (= i (.getNumberOfSheets workbook))
      sheetit
      (let [s (.getSheetAt workbook i)]
        (recur (assoc sheetit (.toLowerCase (.getSheetName s)) s)
               (inc i))))))

(defn lue-vakio-osa [sheetit]
  (let [vakio-osa (get sheetit "vakio_osa")
        solu (fn [s r]
               (some-> vakio-osa
                       (.getRow r)
                       (.getCell s)
                       str))
        rivi (fn [r]
               (mapv #(solu % r) (range 5)))]
    (assert (not (nil? vakio-osa)) "Ei löydy vakio_osa sheettiä!")
    (loop [tiedot []
           i 3]
      (if (> i 30) ;; FIXME: oikea ehto
        tiedot
        (let [r (rivi i)]
          (recur (if (str/blank? (first r))
                   tiedot
                   (conj tiedot (zipmap [:nimi :tyyppi :pituus :pakollisuus :selite] r)))
                 (inc i)))))))

(defn lue-varuste-skeema [sheetti]
  (let [solu (fn [s r]
               (some-> sheetti
                       (.getRow r)
                       (.getCell s)
                       str))]
    (let [alku (loop [r 0]
                 (if (= "Kenttä" (solu 0 r))
                   r
                   (do
                     (assert (< r 20) "Ei löydy Kenttä headeria tältä sheetiltä!")
                     (recur (inc r)))))]
      (loop [kentat []
             r (inc alku)]
        (if (str/blank? (solu 0 r))
          kentat
          (do 
            (assert (< r 100) "Ihan liikaa kenttiä")
            (let [rivi (mapv #(solu % r) (range 8))]
              (recur (conj kentat (zipmap [:nimi :tyyppi :pituus :pakollisuus :selite]
                                          rivi))
                     (inc r)))))))))
             
(defn muodosta-json-schema [vakio tietolaji]
  (let [kentat (map #(update-in % [:nimi]
                                (fn [n]
                                  (-> n
                                      .toLowerCase
                                      (.replace "\u00e4" "a")))) (concat vakio (:kentat tietolaji)))
        pakolliset (keep #(when (= "P" (:pakollisuus %))
                            (:nimi %))
                         kentat)]
    (with-out-str
      (json/pprint 
       {:$schema "http://json-schema.org/draft-04/schema#"
        :id "http://jsonschema.net"
        :title (:kuvaus tietolaji)
        :type "object"
        :properties
        (merge {:tyyppi {:enum [(:tyyppi tietolaji)]}}
               (zipmap (map :nimi  kentat)
                       (map (fn [{:keys [tyyppi selite pituus]}]
                              (let [t (condp = (.toLowerCase tyyppi)
                                       "numero" "number"
                                       
                                       "string")]
                                {:type t
                                 :description selite})) kentat)))
        :required pakolliset}
       :escape-slash false))))

(defn muodosta-json-propertyt [kentat muut-propertyt]
  (let [kentat (map #(update-in % [:nimi]
                                (fn [n]
                                  (some-> n
                                          .toLowerCase
                                          (.replace "\u00e4" "a")))) kentat)
        pakolliset (keep #(when (= "P" (:pakollisuus %))
                            (:nimi %))

                         kentat)]
    (merge
     (if (empty? pakolliset)
       {}
       {:required pakolliset})
     	{:type "object"
         :properties
         (merge (zipmap (map :nimi  kentat)
                        (map (fn [{:keys [tyyppi selite pituus]}]
                               (let [t (condp = (.toLowerCase tyyppi)
                                         "numero" "number"
                                     
                                         "string")]
                                 {:type t
                                  :description selite})) kentat))
                muut-propertyt)})))



(defn lue-skeematiedot [xls]
  (let [sheetit (sheetit xls)]
    (loop [skeemat {:vakio (lue-vakio-osa sheetit)}
           [[nimi sheetti] & sheetit] (seq sheetit)]
      (if-not nimi
        skeemat
        (let [[_ tl _ kuvaus] (re-matches #"(tl\d+)(_| )(.*)" nimi)]
          (if tl
            (recur (assoc skeemat tl
                          {:kuvaus kuvaus
                           :tyyppi tl
                           :kentat (lue-varuste-skeema sheetti)})
                   sheetit)
            (recur skeemat sheetit)))))))

(defn js [thing]
  (with-out-str
    (json/pprint thing :escape-slash false)))
                 
(defn -main [& args]
  (assert (= (count args) 2) "Anna 2 parametria: XLS tiedosto ja hakemisto, jonne skeemat kirjoitetaan.")
  (let [[tiedosto polku] (map io/file args)]
    (assert (.canRead tiedosto) "XLS tiedoston on oltava olemassaoleva tiedosto, joka on luettavissa.")
    (assert (.isDirectory polku) "Polun pitää olla olemassaoleva hakemisto.")
    (let [skeemat (lue-skeematiedot (lue-xls tiedosto))
          vakio (filter #(not= (:nimi %) "tietolaji") (:vakio skeemat))
          skeematiedot (filter #(string? (first %)) (seq skeemat))]

      (doseq [[id skeema] skeematiedot]
        (when (string? id)
          (println "Kirjoitetaan: " (str id ".schema.json"))
          (spit (io/file polku (str id ".schema.json"))
                (js
                 (merge (muodosta-json-propertyt (:kentat skeema) {})
                        {:$schema "http://json-schema.org/draft-04/schema#"
                         :type "object"
                         :description (:nimi skeema)})))))
      
      ;; Kirjoitetaan pääskeema
      (println "Kirjoitetaan: varuste.schema.json")
      (spit (io/file polku "varuste.schema.json")
            (js
             (merge
                (muodosta-json-propertyt vakio
                                         (merge {:tietolaji {:enum (mapv first skeematiedot)}}
                                                (zipmap (map first skeematiedot)
                                                        (map (fn [s]
                                                               {:description (str (first s) " (" (:nimi (second s)) ") tietolajin tiedot.")
                                                                :type "object"
                                                                :$ref (str "file:src/resources/api/schemas/entities/" (first s) ".schema.json")})
                                                             skeematiedot)))
                                         )
                                                
                {:$schema "http://json-schema.org/draft-04/schema#"
                 :type "object"
                 :description "varuste"}
                
                ;;{:properties {:tietolaji {:type "object"
                ;;                         :oneOf (vec (sort-by :$ref
                ;;                                              (keep (fn [id]
                ;;                                                      (when (string? id)
                ;;                                                        {:$ref (str "#/definitions/" id)})) ;;(str "file:src/resources/api/schemas/entities/" id ".schema.json")}))
                ;;                                                    (keys skeematiedot))))}}
                ))
               ))

      ))











