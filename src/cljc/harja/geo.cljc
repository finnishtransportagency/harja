(ns harja.geo
  "Yleisiä geometria-apureita"
  #?(:clj
     (:import (org.postgresql.geometric PGpoint PGpolygon)
           (org.postgis PGgeometry MultiPolygon Polygon Point MultiLineString LineString GeometryCollection Geometry))))

#?(:clj
   (defprotocol MuunnaGeometria
     "Geometriatyyppien muunnos PostgreSQL muodosta Clojure dataksi"
     (pg->clj [this])))

#?(:clj
   (defn piste-koordinaatit [p]
     [(.x p) (.y p)]))

#?(:clj
   (extend-protocol MuunnaGeometria

     ;; PGgeometry tyypin mukaan
     PGgeometry
     (pg->clj [^PGgeometry g]
       (pg->clj (.getGeometry g)))


     GeometryCollection
     (pg->clj [^GeometryCollection gc]
       {:type :geometry-collection
        :geometries (into []
                          (map pg->clj)
                          (.getGeometries gc))})
     
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
     (pg->clj [_] nil)))

#?(:clj
   (defn luo-point [[x y]]
     (PGpoint. x y)))

#?(:clj
   (defmulti clj->pg (fn [geometria]
                       (if (vector? geometria)
                         :geometry-collection
                         (:type geometria)))))

#?(:clj
   (defmethod clj->pg :geometry-collection [geometriat]
     (if (= 1 (count geometriat))
       (clj->pg (first geometriat))
       (GeometryCollection. (into-array Geometry
                                        (map clj->pg geometriat))))))

#?(:clj
   (defmethod clj->pg :multiline [{lines :lines}]
     (MultiLineString. (into-array LineString
                                   (map clj->pg lines)))))

#?(:clj
   (defmethod clj->pg :line [{points :points}]
     (LineString. (into-array Point
                              (map (fn [[x y]]
                                     (Point. x y))
                                   points)))))

#?(:clj
   (defmethod clj->pg :point [{c :coordinates :as p}]
     (Point. (first c) (second c))))

#?(:clj
   (defn geometry [g]
     (PGgeometry. g)))

#?(:clj
   (defmacro muunna-pg-tulokset
     "Palauttaa transducerin, joka muuntaa jokaisen SQL tulosrivin annetut sarakkeet PG geometriatyypeistä Clojure dataksi."
     [& sarakkeet]
     (let [tulosrivi (gensym)]
       `(map (fn [~tulosrivi]
               (assoc ~tulosrivi
                      ~@(mapcat (fn [sarake]
                                  [sarake `(pg->clj (get ~tulosrivi ~sarake))])
                                sarakkeet)))))))

#?(:clj
   (def wgs84-wkt "PROJCS[\"WGS 84 / Pseudo-Mercator\", \n  GEOGCS[\"WGS 84\", \n    DATUM[\"World Geodetic System 1984\", \n      SPHEROID[\"WGS 84\", 6378137.0, 298.257223563, AUTHORITY[\"EPSG\",\"7030\"]], \n      AUTHORITY[\"EPSG\",\"6326\"]], \n    PRIMEM[\"Greenwich\", 0.0, AUTHORITY[\"EPSG\",\"8901\"]], \n    UNIT[\"degree\", 0.017453292519943295], \n    AXIS[\"Geodetic longitude\", EAST], \n    AXIS[\"Geodetic latitude\", NORTH], \n    AUTHORITY[\"EPSG\",\"4326\"]], \n  PROJECTION[\"Popular Visualisation Pseudo Mercator\"], \n  PARAMETER[\"semi_minor\", 6378137.0], \n  PARAMETER[\"latitude_of_origin\", 0.0], \n  PARAMETER[\"central_meridian\", 0.0], \n  PARAMETER[\"scale_factor\", 1.0], \n  PARAMETER[\"false_easting\", 0.0], \n  PARAMETER[\"false_northing\", 0.0], \n  UNIT[\"m\", 1.0], \n  AXIS[\"Easting\", EAST], \n  AXIS[\"Northing\", NORTH], \n  AUTHORITY[\"EPSG\",\"3857\"]]"))

#?(:clj
   (def euref-wkt "PROJCS[\"EUREF_FIN_TM35FIN\", \n  GEOGCS[\"GCS_EUREF_FIN\", \n    DATUM[\"D_ETRS_1989\", \n      SPHEROID[\"GRS_1980\", 6378137.0, 298.257222101]], \n    PRIMEM[\"Greenwich\", 0.0], \n    UNIT[\"degree\", 0.017453292519943295], \n    AXIS[\"Longitude\", EAST], \n    AXIS[\"Latitude\", NORTH]], \n  PROJECTION[\"Transverse_Mercator\"], \n  PARAMETER[\"central_meridian\", 27.0], \n  PARAMETER[\"latitude_of_origin\", 0.0], \n  PARAMETER[\"scale_factor\", 0.9996], \n  PARAMETER[\"false_easting\", 500000.0], \n  PARAMETER[\"false_northing\", 0.0], \n  UNIT[\"m\", 1.0], \n  AXIS[\"x\", EAST], \n  AXIS[\"y\", NORTH]]"))

#?(:clj
   (def wgs84 org.geotools.referencing.crs.DefaultGeographicCRS/WGS84)) ; (org.geotools.referencing.CRS/parseWKT osm-wkt))
#?(:clj
   (def euref (org.geotools.referencing.CRS/parseWKT euref-wkt)))
#?(:clj
   (def euref->wgs84-transform (org.geotools.referencing.CRS/findMathTransform euref wgs84 true)))

#?(:clj
   (defn euref->wgs84
     "Muunnetaan WGS84 (GPS) koordinaatistoon"
     [coordinate]
     (if (vector? coordinate)
       (let [c (org.geotools.geometry.jts.JTS/transform
                (com.vividsolutions.jts.geom.Coordinate. (first coordinate) (second coordinate))
                nil euref->wgs84-transform)]
         [(.y c) (.x c)])
       (org.geotools.geometry.jts.JTS/transform coordinate nil euref->wgs84-transform))))

#?(:clj
   (def wgs84->euref-transform (org.geotools.referencing.CRS/findMathTransform wgs84 euref true)))

#?(:clj
   (defn wgs84->euref
     [coord]
     (let [c (org.geotools.geometry.jts.JTS/transform (com.vividsolutions.jts.geom.Coordinate. (:x coord) (:y coord))
                                                      nil wgs84->euref-transform)]
       {:x (.y c) :y (.x c)})))




(defn- laske-pisteiden-extent
  "Laskee pisteiden alueen."
  [pisteet]
  (let [[ensimmainen-x ensimmainen-y] (first pisteet)
        pisteet (rest pisteet)]
    (loop [minx ensimmainen-x
           miny ensimmainen-y
           maxx ensimmainen-x
           maxy ensimmainen-y
           [[x y] & pisteet] pisteet]
    (if-not x
      [minx miny maxx maxy]
      (recur (min x minx)
             (min y miny)
             (max x maxx)
             (max y maxy)
             pisteet)))))


(defn laajenna-extent [[minx miny maxx maxy] d]
  [(- minx d) (- miny d) (+ maxx d) (+ maxy d)])

(defn extent-keskipiste [[minx miny maxx maxy]]
  (let [width (- maxx minx)
        height (- maxy miny)]
    [(+ minx (/ width 2))
     (+ miny (/ height 2))]))

(defn yhdista-extent
  "Yhdistää kaksi annettua extentiä ja palauttaa uuden extentin,
  johon molemmat mahtuvat"
  ([] nil)
  ([[e1-minx e1-miny e1-maxx e1-maxy] [e2-minx e2-miny e2-maxx e2-maxy]]
   [(Math/min e1-minx e2-minx) (Math/min e1-miny e2-miny)
    (Math/max e1-maxx e2-maxx) (Math/max e1-maxy e2-maxy)]))

(defn- pisteet
  "Palauttaa annetun geometrian pisteet sekvenssinä"
  [{type :type :as g}]
  (case type
    :line (:points g)
    :multiline (mapcat :points (:lines g))
    :polygon (:coordinates g)
    :multipolygon (mapcat :coordinates (:polygons g))
    :point [(:coordinates g)]
    :icon [(:coordinates g)]
    :circle [(:coordinates g)]
    :viiva (:points g)
    :merkki [(:coordinates g)]))

(defn laske-extent-xf
  "Luo transducerin, joka laskee extentiä läpi menevistä geometrioista ja
lopuksi kirjoittaa sen annettuun volatileen."
  [extent-volatile]
  (assert (volatile? extent-volatile) "Anna volatile!, johon extent palautetaan")
  (fn [xf]
    (let [minx (volatile! nil)
          miny (volatile! nil)
          maxx (volatile! nil)
          maxy (volatile! nil)]
      (fn
        ([] (xf))
        ([result]
         (vreset! extent-volatile
                  (when @minx
                    [@minx @miny @maxx @maxy]))
         (xf result))
        ([result input]
         (when-let [alue (:alue input)]
           (loop [minx- @minx
                  miny- @miny
                  maxx- @maxx
                  maxy- @maxy
                  [[x y] & pisteet] (pisteet alue)]
             (if-not x
               (do (vreset! minx minx-)
                   (vreset! miny miny-)
                   (vreset! maxx maxx-)
                   (vreset! maxy maxy-))
               (if minx-
                 (recur (Math/min minx- x) (Math/min miny- y)
                        (Math/max maxx- x) (Math/max maxy- y)
                        pisteet)
                 (recur x y x y pisteet)))))
         (xf result input))))))

(defn keskipiste
  "Laske annetun geometrian keskipiste ottamalla keskiarvon kaikista pisteistä.
Tähän lienee parempiakin tapoja, ks. https://en.wikipedia.org/wiki/Centroid "
  [g]
  (loop [x 0
         y 0
         i 0
         [piste & pisteet] (pisteet g)]
    (if-not piste
      (if (zero? i)
        nil ; ei pisteitä
        [(/ x i) (/ y i)])
      (recur (+ x (first piste))
             (+ y (second piste))
             (inc i)
             pisteet))))



(defmulti extent (fn [geometry] (:type geometry)))

(defmethod extent :line [{points :points}]
  (laske-pisteiden-extent points))

(defmethod extent :multiline [{lines :lines}]
  (laske-pisteiden-extent (mapcat :points lines)))

;; Kuinka paljon yksittäisen pisteen extentiä laajennetaan joka suuntaan
(def pisteen-extent-laajennus 2000)

(defn- extent-point-circle [c]
  (let [d pisteen-extent-laajennus
        [x y] c]
    [(- x d) (- y d) (+ x d) (+ y d)]))

(defmethod extent :point [{c :coordinates}]
  (extent-point-circle c))

(defmethod extent :circle [{c :coordinates}]
  (extent-point-circle c))

(defmethod extent :icon [{c :coordinates}]
  (extent-point-circle c))

(defmethod extent :merkki [{c :coordinates}]
  (extent-point-circle c))

(defmethod extent :viiva [{points :points}]
  (laske-pisteiden-extent points))

(defmethod extent :multipolygon [{polygons :polygons}]
  (laske-pisteiden-extent (mapcat :coordinates polygons)))

(defmethod extent :polygon [{coordinates :coordinates}]
  (laske-pisteiden-extent coordinates))

(defn extent-monelle [geometriat]
  (laske-pisteiden-extent (mapcat pisteet geometriat)))

(defn extent-hypotenuusa
  "Laskee extent hypotenuusan, jotta tiedetään minkä kokoista aluetta katsotaan."
  [[x1 y1 x2 y2]]
  (let [dx (- x2 x1)
        dy (- y2 y1)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

;; Päättelee annetulle geometrialle hyvän ikonisijainnin
;; geometry -> [x y]
(defmulti ikonin-sijainti (fn [geometry] (:type geometry)))

(defmethod ikonin-sijainti :point [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :circle [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :merkki [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :icon [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :default [g]
  (keskipiste g))
