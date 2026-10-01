(ns harja.palvelin.ajastetut-tehtavat.arkistonkasittely-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.palvelin.tyokalut.arkisto :as arkisto]
            [harja.palvelin.tyokalut.kansio :as kansio]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.util.zip ZipEntry ZipOutputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveEntry TarArchiveOutputStream)
           (org.apache.commons.compress.compressors.gzip GzipCompressorOutputStream)))

(def +arkistot-polku+ "test/resurssit/arkistot/")
(def +arkistot-target-polku+ "test/resurssit/arkistot/arkisto_target/")

(defn testaa-tiedoston-purku
  "Purkaa arkiston ja tarkistaa sisällön. Puretut tiedostot kopioidaan testikohtaiseen
   kohdekansioon, jonka tyhjennys varmistetaan. Oma kansio per testi, jotta testien tiedostot eivät
   overlappaa."
  [tiedosto-nimi]
  (let [testikansio (io/file +arkistot-target-polku+ (str/replace tiedosto-nimi "." "_"))
        arkisto-tiedosto (io/file testikansio tiedosto-nimi)
        teksti (io/file testikansio "teksti.txt")
        kuva (io/file testikansio "kuva.png")
        kohde-kansio (io/file testikansio "kohde")]
    (try
      (.mkdirs kohde-kansio)
      ;; Kopioi arkisto-tiedosto testikansioon, jotta alkuperäinen resurssi pysyy kunnossa
      (io/copy (io/file +arkistot-polku+ tiedosto-nimi) arkisto-tiedosto)
      ;; Pura testikansiossa oleva arkisto-tiedosto
      (arkisto/pura-paketti (.getPath arkisto-tiedosto))
      ;; Tarkista, että tiedostot purkautuivat oikein
      (is (.exists teksti))
      (is (= "Terve!" (slurp teksti)))
      (is (.exists kuva))
      ;; Kopioi puretut tiedostot kohdekansioon ja testaa, että kansion tyhjennys toimii
      (io/copy teksti (io/file kohde-kansio "teksti.txt"))
      (io/copy kuva (io/file kohde-kansio "kuva.png"))
      (kansio/poista-tiedostot (.getPath kohde-kansio))
      (is (zero? (count (.listFiles kohde-kansio))) "poista-tiedostot tyhjentää kohdekansion")
      (finally
        (io/delete-file arkisto-tiedosto true)
        (io/delete-file teksti true)
        (io/delete-file kuva true)
        (kansio/poista-tiedostot (.getPath kohde-kansio))
        (io/delete-file kohde-kansio true)
        (io/delete-file testikansio true)))))

(deftest testaa-pura-macissa-tehty-zip
  (testaa-tiedoston-purku "test_zip_mac.zip"))

(deftest testaa-pura-macissa-tehty-gzip
  (testaa-tiedoston-purku "test_gzip_mac.tgz"))

(deftest testaa-pura-zip
  (testaa-tiedoston-purku "test_zip.zip"))

(deftest testaa-pura-gzip
  (testaa-tiedoston-purku "test_gzip.tgz"))

(deftest testaa-pura-pelkka-gzip-tiedosto
  ;; Gzip voi sisältää myös yksittäisen tiedoston ilman tar-arkistoa
  (let [kansio (io/file +arkistot-target-polku+ "pelkka_gzip")
        gz (io/file kansio "teksti.txt.gz")
        purettu (io/file kansio "teksti.txt")]
    (try
      (.mkdirs kansio)
      (with-open [ulos (GzipCompressorOutputStream. (io/output-stream gz))]
        (.write ulos (.getBytes "Terve, terve, tässä on Heikki!")))
      (arkisto/pura-paketti (.getPath gz))
      (is (.exists purettu))
      (is (= "Terve, terve, tässä on Heikki!" (slurp purettu)))
      (finally
        (io/delete-file gz true)
        (io/delete-file purettu true)
        (io/delete-file kansio true)))))

;; Arkisto voi sisältää hakemistoja, joilla ei ole sisältöä. Hakemistot luodaan automaattisesti kohdepolkuun.
;; Hakemiston nimi on parametrisoitu, jotta mahd. rinnakkain ajettavat testit eivät käytä samaa kohdekansiota.
(defn- luo-hakemistollinen-zip [polku hakemisto tyhja-hakemisto?]
  (with-open [ulos (ZipOutputStream. (io/output-stream polku))]
    (.putNextEntry ulos (ZipEntry. (str hakemisto "/")))
    (.closeEntry ulos)
    (when-not tyhja-hakemisto?
      (.putNextEntry ulos (ZipEntry. (str hakemisto "/teksti.txt")))
      (.write ulos (.getBytes "Test"))
      (.closeEntry ulos))))

(defn- luo-hakemistollinen-tgz [polku hakemisto tyhja-hakemisto?]
  (with-open [ulos (TarArchiveOutputStream.
                     (GzipCompressorOutputStream. (io/output-stream polku)))]
    (.putArchiveEntry ulos (TarArchiveEntry. (str hakemisto "/")))
    (.closeArchiveEntry ulos)
    (when-not tyhja-hakemisto?
      (let [sisalto (.getBytes "Test")
            entry (doto (TarArchiveEntry. (str hakemisto "/teksti.txt"))
                    (.setSize (count sisalto)))]
        (.putArchiveEntry ulos entry)
        (.write ulos sisalto)
        (.closeArchiveEntry ulos)))))

(defn- testaa-hakemistollisen-arkiston-purku
  "Luo arkiston, purkaa sen ja varmistaa että hakemisto syntyy kohdekansioon.
   Kun tyhja-hakemisto? on false (oletus), tarkistetaan myös hakemiston sisältö.
   Arkiston sisältämä hakemisto nimetään arkiston tiedostonimen mukaan, jolloin
   jokainen testi käyttää omaa uniikkia kohdekansiotaan, eikä overlappaa muiden testien kanssa."
  ([tiedosto-nimi luo-arkisto-fn]
   (testaa-hakemistollisen-arkiston-purku tiedosto-nimi luo-arkisto-fn false))
  ([tiedosto-nimi luo-arkisto-fn tyhja-hakemisto?]
   (let [kansio (io/file +arkistot-target-polku+)
         arkisto (io/file kansio tiedosto-nimi)
         hakemisto-nimi (str/replace tiedosto-nimi "." "_")
         alikansio (io/file kansio hakemisto-nimi)
         purettu (io/file alikansio "teksti.txt")]
     (try
       (luo-arkisto-fn (.getPath arkisto) hakemisto-nimi tyhja-hakemisto?)
       (arkisto/pura-paketti (.getPath arkisto))

       (is (.isDirectory alikansio))

       (when-not tyhja-hakemisto?
         (is (= "Test" (slurp purettu))))
       (finally
         (io/delete-file arkisto true)
         (io/delete-file purettu true)
         (io/delete-file alikansio true))))))

(deftest testaa-pura-hakemistoja-sisaltava-zip
  (testaa-hakemistollisen-arkiston-purku "hakemistot_zip.zip" luo-hakemistollinen-zip))

(deftest testaa-pura-hakemistoja-sisaltava-tgz
  (testaa-hakemistollisen-arkiston-purku "hakemistot_tgz.tgz" luo-hakemistollinen-tgz))

(deftest testaa-pura-tyhjan-hakemiston-sisaltava-zip
  (testaa-hakemistollisen-arkiston-purku "tyhja_hakemisto_zip.zip" luo-hakemistollinen-zip true))

(deftest testaa-pura-tyhjan-hakemiston-sisaltava-tgz
  (testaa-hakemistollisen-arkiston-purku "tyhja_hakemisto_tgz.tgz" luo-hakemistollinen-tgz true))

;; Tietoturva: Path traversal -suojaus
;; Info: https://cwe.mitre.org/data/definitions/22.html

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

(defn- testaa-path-traversal
  "Purkaa 'pahan arkiston', joka yrittää kirjoittaa kohdekansion ulkopuolelle (../paha.txt).
   Arkisto puretaan testikohtaiseen hakemistoon, jolloin mahdollinen hakemiston ulkopuolelle
   karkaava tiedosto puretaan testikohtaiseen hakemistoon, eikä jaettuun kohdekansioon."
  [tiedosto-nimi luo-arkisto-fn]
  (let [testikansio (io/file +arkistot-target-polku+ (str/replace tiedosto-nimi "." "_"))
        kohde-kansio (io/file testikansio "arkisto")
        arkisto (io/file kohde-kansio tiedosto-nimi)
        paha-tiedosto (io/file testikansio "paha.txt")]
    (try
      (.mkdirs kohde-kansio)
      (luo-arkisto-fn (.getPath arkisto))
      (is (thrown? clojure.lang.ExceptionInfo (arkisto/pura-paketti (.getPath arkisto))))
      (is (false? (.exists paha-tiedosto))
        "Arkiston purku ei saa kirjoittaa kohdekansion ulkopuolelle")
      (finally
        (io/delete-file arkisto true)
        (io/delete-file paha-tiedosto true)
        (io/delete-file kohde-kansio true)
        (io/delete-file testikansio true)))))

(deftest testaa-zip-ei-purkaudu-kohdekansion-ulkopuolelle
  (testaa-path-traversal "paha.zip" luo-paha-zip))

(deftest testaa-gzip-ei-purkaudu-kohdekansion-ulkopuolelle
  (testaa-path-traversal "paha.tgz" luo-paha-tgz))
