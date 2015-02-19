(ns harja.shp
  "Shape filejen käsittelyn apureita"
  (:import (org.geotools.data.shapefile ShapefileDataStore)
           (org.geotools.map MapContent FeatureLayer))
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn lue-shapefile [tiedosto]
  (-> tiedosto
      io/as-url
      ShapefileDataStore.))
      

(defn featuret
  "Palauttaa shapefilen featuret listana."
  [shp]
  (-> shp
      .getFeatureSource
      .getFeatures
      .toArray
      seq))

(defn feature-propertyt
  "Muuntaa yhden featuren kaikki property mäpiksi, jossa avain on keyword."
  [feature]
  (loop [acc {}
         [p & ps] (seq (.getProperties feature))]
    (if-not p
      acc
      (recur (assoc acc
               (keyword (.toLowerCase (.getLocalPart (.getName p))))
               (.getValue p))
             ps))))

(defn suljettu-rengas [koordinaatit]
  (let [koordinaatit (vec koordinaatit)
        eka (first koordinaatit)
        toka (last koordinaatit)]
    (if (not= eka toka)
      (conj koordinaatit eka)
      koordinaatit)))

(defn geom->pg [alue]
  (if (= 1 (.getNumGeometries alue))
    ;; Sisältää yksittäisen polygonin, otetaan se vain
    (let [p (.getGeometryN alue 0)]
      (str "ST_GeomFromText('POLYGON(("
           (str/join ", "
                     (map #(str (.x %) " " (.y %)) (suljettu-rengas (seq (.getCoordinates p))))) ;;"x1 y1, x2 y2, xN yN"
           "))')::GEOMETRY"))

    ;; useita polygoneja
    (str "ST_GeomFromText('MULTIPOLYGON("
         (str/join ","
                   (loop [acc []
                          i 0]
                     (if (= i (.getNumGeometries alue))
                       acc
                       (recur (conj acc
                                    (str "((" (str/join ", "
                                                        (map #(str (.x %) " " (.y %))
                                                             (suljettu-rengas (seq (.getCoordinates (.getGeometryN alue i)))))) "))"))
                              (inc i)))))
         ")')::GEOMETRY")))

