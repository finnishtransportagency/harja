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

(defn- spacella-erotetuksi-pariksi [koordinaatti]
  (str (.x koordinaatti) " " (.y koordinaatti)))

(defn- pilkkulistaksi [koordinaattilista]
  (str/join ", " (map spacella-erotetuksi-pariksi (suljettu-rengas (seq koordinaattilista)))))

(defn- polygoni [koordinaattilista]
  (str "ST_GeomFromText('POLYGON((" koordinaattilista "))')::GEOMETRY"))

(defn- multipolygoni [koordinaattilista]
  (str "ST_GeomFromText('MULTIPOLYGON(" koordinaattilista ")')::GEOMETRY"))

(defn geom->pg [alue]
  (if (= 1 (.getNumGeometries alue))
    ;; Sisältää yksittäisen polygonin, otetaan se vain
    (let [p (.getGeometryN alue 0)]
      ;;"x1 y1, x2 y2, xN yN"
      (polygoni (pilkkulistaksi (.getCoordinates p))))

    ;; useita polygoneja
    (multipolygoni
     (str/join ","
               (loop [acc []
                      i 0]
                 (if (= i (.getNumGeometries alue))
                   acc
                   (recur (conj acc
                                (str "((" (pilkkulistaksi (.getCoordinates (.getGeometryN alue i))) "))"))
                          (inc i))))))))
