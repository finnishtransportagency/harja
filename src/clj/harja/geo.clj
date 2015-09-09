(ns harja.geo
  "Yleiskäyttöisiä paikkatietoon ja koordinaatteihin liittyviä apureita."
  (:import (org.postgresql.geometric PGpoint PGpolygon)
           (org.postgis PGgeometry MultiPolygon Polygon Point MultiLineString LineString)))

(declare euref->osm)

(defprotocol MuunnaGeometria
  "Geometriatyyppien muunnos PostgreSQL muodosta Clojure dataksi"
  (pg->clj [this]))

(defn piste-koordinaatit [p]
  [(.x p) (.y p)])

(extend-protocol MuunnaGeometria

  ;; PGgeometry tyypin mukaan
  PGgeometry
  (pg->clj [^PGgeometry g]
    (pg->clj (.getGeometry g)))


  MultiPolygon
  (pg->clj [^MultiPolygon mp]
    {:type :multipolygon
     :polygons (mapv pg->clj (seq (.getPolygons mp)))})

  Polygon
  (pg->clj [^Polygon p]
    {:type :polygon
     :coordinates (mapv piste-koordinaatit
                        (loop [acc []
                               i 0]
                          (if (= i (.numPoints p))
                            acc
                            (recur (conj acc (.getPoint p i))
                                   (inc i)))))})

  Point 
  (pg->clj [^Point p]
    {:type :point
     :coordinates (piste-koordinaatit p)})
  
  PGpoint
  (pg->clj [^PGpoint p]
    {:type :point
     :coordinates (piste-koordinaatit p)})

  PGpolygon
  (pg->clj [^PGpolygon poly]
    {:type :polygon
     :coordinates (mapv piste-koordinaatit
                        (seq (.points poly)))})

  LineString
  (pg->clj [^LineString line]
    {:type :line
     :points (mapv piste-koordinaatit (.getPoints line))})
  
  MultiLineString
  (pg->clj [^MultiLineString mls]
    {:type :multiline
     :lines (mapv pg->clj (.getLines mls))})
  ;; NULL geometriaoli on myös nil Clojure puolella
  nil
  (pg->clj [_] nil))

(defn luo-point [[x y]]
  (PGpoint. x y))

(defmulti clj->pg (fn [geometria] (:type geometria)))

(defmethod clj->pg :multiline [{lines :lines}]
  (MultiLineString. (into-array LineString
                                (map clj->pg lines))))

(defmethod clj->pg :line [{points :points}]
  (LineString. (into-array Point
                           (map (fn [[x y]]
                                  (Point. x y))
                                points))))
(defn geometry [g]
  (PGgeometry. g))

(defmacro muunna-pg-tulokset
  "Palauttaa transducerin, joka muuntaa jokaisen SQL tulosrivin annetut sarakkeet PG geometriatyypeistä Clojure dataksi."
  [& sarakkeet]
  (let [tulosrivi (gensym)]
    `(map (fn [~tulosrivi]
            (assoc ~tulosrivi
              ~@(mapcat (fn [sarake]
                          [sarake `(pg->clj (get ~tulosrivi ~sarake))])
                        sarakkeet))))))



(def osm-wkt "PROJCS[\"WGS 84 / Pseudo-Mercator\", \n  GEOGCS[\"WGS 84\", \n    DATUM[\"World Geodetic System 1984\", \n      SPHEROID[\"WGS 84\", 6378137.0, 298.257223563, AUTHORITY[\"EPSG\",\"7030\"]], \n      AUTHORITY[\"EPSG\",\"6326\"]], \n    PRIMEM[\"Greenwich\", 0.0, AUTHORITY[\"EPSG\",\"8901\"]], \n    UNIT[\"degree\", 0.017453292519943295], \n    AXIS[\"Geodetic longitude\", EAST], \n    AXIS[\"Geodetic latitude\", NORTH], \n    AUTHORITY[\"EPSG\",\"4326\"]], \n  PROJECTION[\"Popular Visualisation Pseudo Mercator\"], \n  PARAMETER[\"semi_minor\", 6378137.0], \n  PARAMETER[\"latitude_of_origin\", 0.0], \n  PARAMETER[\"central_meridian\", 0.0], \n  PARAMETER[\"scale_factor\", 1.0], \n  PARAMETER[\"false_easting\", 0.0], \n  PARAMETER[\"false_northing\", 0.0], \n  UNIT[\"m\", 1.0], \n  AXIS[\"Easting\", EAST], \n  AXIS[\"Northing\", NORTH], \n  AUTHORITY[\"EPSG\",\"3857\"]]")

(def euref-wkt "PROJCS[\"EUREF_FIN_TM35FIN\", \n  GEOGCS[\"GCS_EUREF_FIN\", \n    DATUM[\"D_ETRS_1989\", \n      SPHEROID[\"GRS_1980\", 6378137.0, 298.257222101]], \n    PRIMEM[\"Greenwich\", 0.0], \n    UNIT[\"degree\", 0.017453292519943295], \n    AXIS[\"Longitude\", EAST], \n    AXIS[\"Latitude\", NORTH]], \n  PROJECTION[\"Transverse_Mercator\"], \n  PARAMETER[\"central_meridian\", 27.0], \n  PARAMETER[\"latitude_of_origin\", 0.0], \n  PARAMETER[\"scale_factor\", 0.9996], \n  PARAMETER[\"false_easting\", 500000.0], \n  PARAMETER[\"false_northing\", 0.0], \n  UNIT[\"m\", 1.0], \n  AXIS[\"x\", EAST], \n  AXIS[\"y\", NORTH]]")

(def osm org.geotools.referencing.crs.DefaultGeographicCRS/WGS84) ; (org.geotools.referencing.CRS/parseWKT osm-wkt))
(def euref (org.geotools.referencing.CRS/parseWKT euref-wkt))
(def euref->osm-transform (org.geotools.referencing.CRS/findMathTransform euref osm true))

(defn euref->osm
  "Muunnetaan OSM koordinaatistoon, tätä ei tarvita enää kun meillä on MML kartat"
  [coordinate]
  (if (vector? coordinate)
    (let [c (org.geotools.geometry.jts.JTS/transform (com.vividsolutions.jts.geom.Coordinate. (first coordinate) (second coordinate))
                                                     nil euref->osm-transform)]
      [(.y c) (.x c)])
    (org.geotools.geometry.jts.JTS/transform coordinate nil euref->osm-transform)))

