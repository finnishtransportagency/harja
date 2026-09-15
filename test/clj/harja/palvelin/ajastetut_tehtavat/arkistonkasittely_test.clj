(ns harja.palvelin.ajastetut-tehtavat.arkistonkasittely-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.testi :refer :all]
            [harja.palvelin.tyokalut.arkisto :as arkisto]
            [harja.palvelin.tyokalut.kansio :as kansio]
            [clojure.java.io :as io])
  (:import (java.util.zip ZipEntry ZipOutputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveEntry TarArchiveOutputStream)
           (org.apache.commons.compress.compressors.gzip GzipCompressorOutputStream)))

(def +arkistot-polku+ "test/resurssit/arkistot/")
(def +arkistot-target-polku+ "test/resurssit/arkistot/arkisto_target/")

(defn testaa-tiedoston-purku [tiedosto-nimi]
  (arkisto/pura-paketti (str +arkistot-polku+ tiedosto-nimi))
  ; Tarkista, että tiedostot purkautuivat oikein
  (is (true? (.exists (clojure.java.io/file (str +arkistot-polku+ "teksti.txt")))))
  (is (= "Terve!" (slurp (str +arkistot-polku+ "teksti.txt"))))
  (is (true? (.exists (clojure.java.io/file (str +arkistot-polku+ "kuva.png")))))
  ; Siirrä puretut tiedostot target-kansioon
  (io/copy (io/file (str +arkistot-polku+ "teksti.txt")) (io/file (str +arkistot-target-polku+ "teksti.txt")))
  (io/copy (io/file (str +arkistot-polku+ "kuva.png")) (io/file (str +arkistot-target-polku+ "kuva.png")))
  (clojure.java.io/delete-file (str +arkistot-polku+ "kuva.png"))
  (clojure.java.io/delete-file (str +arkistot-polku+ "teksti.txt"))
  ; Tyhjennä target-kansio
  (kansio/poista-tiedostot +arkistot-target-polku+)
  (is (= 1 (count (.listFiles (clojure.java.io/file +arkistot-target-polku+)))))) ;; .gitkeep tiedosto jää

(deftest testaa-pura-macissa-tehty-zip
  (testaa-tiedoston-purku "test_zip_mac.zip"))

(deftest testaa-pura-macissa-tehty-gzip
  (testaa-tiedoston-purku "test_gzip_mac.tgz"))

(deftest testaa-pura-zip
  (testaa-tiedoston-purku "test_zip.zip"))

(deftest testaa-pura-gzip
  (testaa-tiedoston-purku "test_gzip.tgz"))

;; Tietoturva: Path traversal -suojaus
;; Info: https://cwe.mitre.org/data/definitions/22.html

(def +paha-arkisto-polku+ "test/resurssit/arkistot/paha_target/")
(def +paha-tiedosto+ "test/resurssit/arkistot/paha.txt")

(defn- luo-paha-zip [polku]
  (with-open [ulos (ZipOutputStream. (io/output-stream polku))]
    (.putNextEntry ulos (ZipEntry. "../paha.txt"))
    (.write ulos (.getBytes "paha"))
    (.closeEntry ulos)))

(defn- luo-paha-tgz [polku]
  (with-open [ulos (TarArchiveOutputStream.
                     (GzipCompressorOutputStream. (io/output-stream polku)))]
    (let [sisalto (.getBytes "paha")
          entry (doto (TarArchiveEntry. "../paha.txt")
                  (.setSize (count sisalto)))]
      (.putArchiveEntry ulos entry)
      (.write ulos sisalto)
      (.closeArchiveEntry ulos))))

(defn- testaa-path-traversal [tiedosto-nimi luo-arkisto-fn]
  (.mkdirs (io/file +paha-arkisto-polku+))
  (io/delete-file (io/file +paha-tiedosto+) true)
  (let [arkiston-polku (str +paha-arkisto-polku+ tiedosto-nimi)]
    (try
      (luo-arkisto-fn arkiston-polku)
      (is (thrown? clojure.lang.ExceptionInfo (arkisto/pura-paketti arkiston-polku)))
      (is (false? (.exists (io/file +paha-tiedosto+)))
          "Arkiston purku ei saa kirjoittaa kohdekansion ulkopuolelle")
      (finally
        (io/delete-file (io/file arkiston-polku) true)
        (io/delete-file (io/file +paha-tiedosto+) true)
        (io/delete-file (io/file +paha-arkisto-polku+) true)))))

(deftest testaa-zip-ei-purkaudu-kohdekansion-ulkopuolelle
  (testaa-path-traversal "paha.zip" luo-paha-zip))

(deftest testaa-gzip-ei-purkaudu-kohdekansion-ulkopuolelle
  (testaa-path-traversal "paha.tgz" luo-paha-tgz))
