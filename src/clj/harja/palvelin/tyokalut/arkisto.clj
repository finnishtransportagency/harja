(ns harja.palvelin.tyokalut.arkisto
  (:require [clojure.java.io :as io])
  (:import (java.io File)
           (java.nio.file Path)
           (java.util.zip ZipEntry ZipInputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveEntry TarArchiveInputStream)
           (org.apache.commons.compress.compressors.gzip GzipCompressorInputStream GzipCompressorInputStream$Builder)
           (org.apache.commons.io FilenameUtils))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn- turvallinen-kohdetiedosto
  "Muodostaa arkiston kohdetiedoston kohdekansion sisään turvallisesti.
   Heittää poikkeuksen, jos purku yrittäisi kirjoittaa kohdekansion ulkopuolelle (ns. Zip Slip)."
  ^File [kohdekansio kohdetiedosto-nimi]
  (let [^File kansio (.getCanonicalFile (io/file kohdekansio))
        ^File kohdetiedosto (.getCanonicalFile (io/file kansio kohdetiedosto-nimi))]
    (when-not (.startsWith (.toPath kohdetiedosto) ^Path (.toPath kansio))
      (throw+ {:type :arkiston-purku-epaonnistui
               :error (str "Arkiston purku yrittää kirjoittaa kohdekansion ulkopuolelle: " kohdetiedosto-nimi)}))
    kohdetiedosto))

(defn pura-zip-paketti [kohdetiedoston-polku]
  (let [kohdepolku (.getParent (io/file kohdetiedoston-polku))]
    (with-open [zip-virta (ZipInputStream. (io/input-stream kohdetiedoston-polku))]
      (doseq [^ZipEntry tiedosto (repeatedly #(.getNextEntry zip-virta)) :while tiedosto]
        (io/copy zip-virta (turvallinen-kohdetiedosto kohdepolku (.getName tiedosto)))))))

(defn pura-gzip-paketti [kohdetiedoston-polku]
  (let [kohdepolku (.getParent (io/file kohdetiedoston-polku))
        ;; Asetettu decompressConcatenated = true, jotta peräkkäiset GZIP-jäsenet puretaan varmasti
        ;; (vastaa java.util.zip/GZIPInputStream-käytöstä)
        ^GzipCompressorInputStream$Builder gzip-builder (doto
                                                          (GzipCompressorInputStream/builder)
                                                          (.setInputStream (io/input-stream kohdetiedoston-polku))
                                                          (.setDecompressConcatenated true))]
    (with-open [zip-virta (TarArchiveInputStream. (.get gzip-builder))]
      (doseq [^TarArchiveEntry tiedosto (repeatedly #(.getNextEntry zip-virta)) :while tiedosto]
        (io/copy zip-virta (turvallinen-kohdetiedosto kohdepolku (.getName tiedosto)))))))

(defn pura-paketti [kohdetiedoston-polku]
  (let [tiedostotyyppi (FilenameUtils/getExtension kohdetiedoston-polku)]
    (case tiedostotyyppi
      "zip" (pura-zip-paketti kohdetiedoston-polku)
      "shz" (pura-zip-paketti kohdetiedoston-polku)
      "gz" (pura-gzip-paketti kohdetiedoston-polku)
      "tgz" (pura-gzip-paketti kohdetiedoston-polku)
      (throw+ {:type :tuntematon-arkisto-tyyppi
               :error "Ei voida purkaa pakettia: " kohdetiedoston-polku ". Tuntematon tiedostotyyppi: " tiedostotyyppi "."}))))
