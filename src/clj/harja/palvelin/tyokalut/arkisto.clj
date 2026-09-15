(ns harja.palvelin.tyokalut.arkisto
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as log])
  (:import (java.util.zip ZipInputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveInputStream)
           (org.apache.commons.compress.compressors.gzip GzipCompressorInputStream)
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
    ;; Asetettu decompressConcatenated = true, jotta peräkkäiset GZIP-jäsenet puretaan varmasti
    ;; (vastaa java.util.zip/GZIPInputStream-käytöstä)
    (with-open [zip-virta (TarArchiveInputStream.
                            (-> (GzipCompressorInputStream/builder)
                                (.setInputStream (io/input-stream kohdetiedoston-polku))
                                (.setDecompressConcatenated true)
                                (.get)))]
      (doseq [tiedosto (repeatedly #(.getNextEntry zip-virta)) :while tiedosto]
        (log/debug (.replace (str (.getName tiedosto)) "./._" ""))
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
