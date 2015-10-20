(ns harja.palvelin.ajastetut-tehtavat.arkistonkasittely-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.testi :refer :all]
            [harja.palvelin.tyokalut.arkisto :as arkisto]
            [harja.palvelin.tyokalut.kansio :as kansio]
            [clojure.java.io :as io]))

(def +arkistot-polku+ "test/resurssit/arkistot/")
(def +arkistot-target-polku+ "test/resurssit/arkistot/target/")

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
  (is (= 0 (count (.listFiles (clojure.java.io/file +arkistot-target-polku+))))))

(deftest testaa-pura-macissa-tehty-zip
  (testaa-tiedoston-purku"test_zip_mac.zip"))

(deftest testaa-pura-macissa-tehty-gzip
  (testaa-tiedoston-purku"test_gzip_mac.tgz"))

(deftest testaa-pura-zip
  (testaa-tiedoston-purku "test_zip.zip"))

(deftest testaa-pura-gzip
  (testaa-tiedoston-purku "test_gzip.tgz"))