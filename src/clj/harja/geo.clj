(ns harja.geo
  "Yleiskäyttöisiä paikkatietoon ja koordinaatteihin liittyviä apureita."
  (:import (org.postgresql.geometric PGpoint PGpolygon)
           (org.postgis PGgeometry MultiPolygon Polygon Point)))

(declare euref->osm)

(defprotocol MuunnaGeometria
  "Geometriatyyppien muunnos PostgreSQL muodosta Clojure dataksi"
  (pg->clj [this]))

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
     :coordinates (mapv pg->clj
                        (loop [acc []
                               i 0]
                          (if (= i (.numPoints p))
                            acc
                            (recur (conj acc (.getPoint p i))
                                   (inc i)))))})

  Point 
  (pg->clj [^Point p]
    (euref->osm [(.x p) (.y p)]))
  
  ;; Piste muunnetaan muotoon [x y]
  PGpoint
  (pg->clj [^PGpoint p]
    (euref->osm [(.x p) (.y p)]))

  ;; Polygoni muunnetaan muotoon [[x1 y1] ... [xN yN]]
  PGpolygon
  (pg->clj [^PGpolygon poly]
    (mapv pg->clj (seq (.points poly))))

  ;; NULL geometriaoli on myös nil Clojure puolella
  nil
  (pg->clj [_] nil))

(defmacro muunna-pg-tulokset
  "Ottaa sisään SQL haun tulokset ja muuntaa annetut sarakkeet PG geometriatyypeistä Clojure dataksi."
  [tulokset & sarakkeet]
  (let [tulosrivi (gensym)]
    `(map (fn [~tulosrivi]
            (assoc ~tulosrivi
              ~@(mapcat (fn [sarake]
                          [sarake `(pg->clj (get ~tulosrivi ~sarake))])
                        sarakkeet)))
          ~tulokset)))



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

