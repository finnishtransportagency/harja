(ns harja-laadunseuranta.tietokanta
  (:require [harja-laadunseuranta.config :as c]
            [clojure.java.io :as io])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)
           (java.io InputStream ByteArrayOutputStream)
           (org.postgresql.largeobject LargeObjectManager)
           (com.mchange.v2.c3p0 C3P0ProxyConnection)           
           (net.coobird.thumbnailator Thumbnailator)
           (net.coobird.thumbnailator.tasks UnsupportedFormatException)
           (java.util Properties)))

(defn- luo-tietokanta
  [{:keys [palvelin portti tietokanta kayttaja salasana yhteyspoolin-koko]}]
  (System/setProperties
   (doto (new Properties (System/getProperties))
     (.put "com.mchange.v2.log.MLog", "com.mchange.v2.log.FallbackMLog")
     (.put "com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL", "SEVERE")))
  (doto (ComboPooledDataSource.)
    (.setDriverClass "org.postgresql.Driver")
    (.setJdbcUrl (str "jdbc:postgresql://" palvelin ":" portti "/" tietokanta))
    (.setUser kayttaja)
    (.setPassword salasana)
    (.setMaxPoolSize (or yhteyspoolin-koko 16))
    (.setMaxIdleTimeExcessConnections (* 30 60))
    (.setMaxIdleTime (* 3 60 60))))

(def db (delay {:datasource (luo-tietokanta (:tietokanta @c/config))}))

(def get-large-object-api (->> (Class/forName "org.postgresql.PGConnection")
                               .getMethods
                               (filter #(= (.getName %) "getLargeObjectAPI"))
                               first))

(defn- large-object-api [c]
  (.rawConnectionOperation c
                           get-large-object-api
                           C3P0ProxyConnection/RAW_CONNECTION
                           (into-array Object [])))

(defn- lue-lob [oid]
  (with-open [c (doto (.getConnection (:datasource @db))
                  (.setAutoCommit false))]
    (let [lom (large-object-api c)]
      (with-open [obj (.open lom oid LargeObjectManager/READ)
                  in (.getInputStream obj)
                  out (ByteArrayOutputStream.)]
        (io/copy in out)
        (.toByteArray out)))))

(defn tallenna-lob [^InputStream in]
  (with-open [c (doto (.getConnection (:datasource @db))
                  (.setAutoCommit false))]
    (let [lom (large-object-api c)
          oid (.create lom LargeObjectManager/READWRITE)]
      (try
        (with-open [obj (.open lom oid LargeObjectManager/WRITE)
                    out (.getOutputStream obj)]
          (io/copy in out)
          oid)
        (finally
          (.commit c))))))

(defn tee-thumbnail [kuva]
  (try
    (with-open [out (ByteArrayOutputStream.)]
      (Thumbnailator/createThumbnail (io/input-stream kuva) out "png" 64 64)
      (.toByteArray out))
    (catch UnsupportedFormatException _ nil)))
