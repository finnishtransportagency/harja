(ns harja.shp
  "Shape filejen käsittelyn apureita"
  (:import (org.geotools.data Query)
   (org.geotools.data.shapefile ShapefileDataStore)
           (org.geotools.map MapContent FeatureLayer)
           (org.geotools.filter SortByImpl)

           (org.opengis.filter.expression PropertyName)
           (org.opengis.filter.sort SortBy SortOrder))
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

(defn- to-iterator [feature-iterator]
  (reify java.util.Iterator
    (hasNext [_] (.hasNext feature-iterator))
    (next [_] (.next feature-iterator))))

(defn featuret-lazy
  ([shp] (featuret-lazy shp nil))
  ([shp q]
   (let [get-features (if q
                        #(.getFeatures % q)
                        #(.getFeatures %))]
     (-> shp .getFeatureSource get-features .features
         to-iterator iterator-seq))))

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

(defn- sort-by-query [shp kentta ascending?]
  (let [sort-by (-> shp .getFilterFactory
                    (.sort kentta (if ascending?
                                    SortOrder/ASCENDING
                                    SortOrder/DESCENDING)))]
    (doto (Query.)
      (.setSortBy (into-array SortBy
                              [sort-by])))))

(defn featuret-ryhmiteltyna
  "Palauttaa shapefilen featuret sortattuna annetun avaimen mukaan"
  [shp kentta callback]

  ;; Tämä pitää tehdä clojuren puolella, jos featureita hakee
  ;; geotoolsin Sort queryllä, häviää featureita, ks. HAR-4685

  (let [ryhmiteltyna (group-by (keyword (str/lower-case kentta))
                               (feature-propertyt (featuret-lazy shp)))]
    (doseq [avain (sort (keys ryhmiteltyna))]
      (callback (get ryhmiteltyna avain)))))



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
