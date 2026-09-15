(ns harja.palvelin.tyokalut.arkisto
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io BufferedInputStream File InputStream)
           (java.nio.file Path)
           (java.util.zip ZipEntry ZipInputStream)
           (org.apache.commons.compress.archivers ArchiveException ArchiveStreamFactory)
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

(defn- tar-arkisto?
  "Tutkii tiedostovirran alusta, onko kyseessä tar-arkisto. Virta on oltava puskuroitu, sillä
   tunnistus lukee vain virran alkua ja palauttaa sen sitten alkutilaan."
  [^BufferedInputStream virta]
  (try
    (= ArchiveStreamFactory/TAR (ArchiveStreamFactory/detect virta))
    (catch ArchiveException _
      false)))

(defn- pura-tar-virta [^InputStream virta kohdepolku]
  (with-open [tar-virta (TarArchiveInputStream. virta)]
    (doseq [^TarArchiveEntry tiedosto (repeatedly #(.getNextEntry tar-virta))
            :while tiedosto]
      (io/copy tar-virta (turvallinen-kohdetiedosto kohdepolku (.getName tiedosto))))))

(defn- pura-gzip-tiedosto
  "Purkaa gzip-pakatun yksittäisen tiedoston kohdekansioon."
  [^InputStream virta kohdepolku kohdetiedoston-polku]
  (let [nimi (FilenameUtils/getBaseName kohdetiedoston-polku)]
    (when (str/blank? nimi)
      (throw+ {:type :arkiston-purku-epaonnistui
               :error (str "Gzip-tiedostolle ei voida päätellä purettavan tiedoston nimeä: " kohdetiedoston-polku)}))
    (io/copy virta (turvallinen-kohdetiedosto kohdepolku nimi))))

(defn pura-gzip-paketti [kohdetiedoston-polku]
  (let [kohdepolku (.getParent (io/file kohdetiedoston-polku))
        ;; Asetettu decompressConcatenated = true, jotta peräkkäiset GZIP-jäsenet puretaan varmasti
        ;; (vastaa java.util.zip/GZIPInputStream-käytöstä)
        ^GzipCompressorInputStream$Builder gzip-builder (doto
                                                          (GzipCompressorInputStream/builder)
                                                          (.setInputStream (io/input-stream kohdetiedoston-polku))
                                                          (.setDecompressConcatenated true))]
    (with-open [gzip-virta (BufferedInputStream. (.get gzip-builder))]
      ;; Annettu "Gzip-tiedosto" voi sisältää joko tar-arkiston (.tar.gz, .tgz) tai yksittäisen pakatun tiedoston (.gz)
      ;; Asian todellinen laita varmistetaan tässä
      (if (tar-arkisto? gzip-virta)
        (pura-tar-virta gzip-virta kohdepolku)
        (pura-gzip-tiedosto gzip-virta kohdepolku kohdetiedoston-polku)))))

(defn pura-paketti [kohdetiedoston-polku]
  (let [tiedostotyyppi (FilenameUtils/getExtension kohdetiedoston-polku)]
    (case tiedostotyyppi
      "zip" (pura-zip-paketti kohdetiedoston-polku)
      "shz" (pura-zip-paketti kohdetiedoston-polku)
      "gz" (pura-gzip-paketti kohdetiedoston-polku)
      "tgz" (pura-gzip-paketti kohdetiedoston-polku)
      (throw+ {:type :tuntematon-arkisto-tyyppi
               :error "Ei voida purkaa pakettia: " kohdetiedoston-polku ". Tuntematon tiedostotyyppi: " tiedostotyyppi "."}))))
