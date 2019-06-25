(ns harja.palvelin.tyokalut.arkisto
  (:require [clojure.java.io :as io])
  (:import (java.util.zip ZipInputStream)
           (java.util.zip GZIPInputStream)
           (org.apache.tools.tar TarInputStream)
           (org.apache.commons.io FilenameUtils))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn pura-zip-paketti [kohdetiedoston-polku]
  (let [kohdepolku (.getParent (io/file kohdetiedoston-polku))]
    (with-open [zip-virta (ZipInputStream. (io/input-stream kohdetiedoston-polku))]
      (doseq [tiedosto (repeatedly #(.getNextEntry zip-virta)) :while tiedosto]
        (let [tiedostopolku (str kohdepolku "/" tiedosto)]
          (io/copy zip-virta (io/file tiedostopolku)))))))

(defn pura-gzip-paketti [kohdetiedoston-polku]
  (let [kohdepolku (.getParent (io/file kohdetiedoston-polku))]
    (with-open [zip-virta (TarInputStream. (GZIPInputStream. (io/input-stream kohdetiedoston-polku)))]
      (doseq [tiedosto (repeatedly #(.getNextEntry zip-virta)) :while tiedosto]
        (println (.replace (str (.getName tiedosto)) "./._" ""))
        (let [tiedostopolku (str kohdepolku "/" (.replace (str (.getName tiedosto)) "./._" ""))]
          (io/copy zip-virta (io/file tiedostopolku)))))))

(defn pura-paketti [kohdetiedoston-polku]
  (let [tiedostotyyppi (FilenameUtils/getExtension kohdetiedoston-polku)]
    (case tiedostotyyppi
      "zip" (pura-zip-paketti kohdetiedoston-polku)
      "shz" (pura-zip-paketti kohdetiedoston-polku)
      "gz" (pura-gzip-paketti kohdetiedoston-polku)
      "tgz" (pura-gzip-paketti kohdetiedoston-polku)
      (throw+ {:type  :tuntematon-arkisto-tyyppi
               :error "Ei voida purkaa pakettia: " kohdetiedoston-polku ". Tuntematon tiedostotyyppi: " tiedostotyyppi "."}))))
