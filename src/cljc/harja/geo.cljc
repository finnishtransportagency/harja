(ns harja.geo
  "Yleisiä geometria-apureita"
  #?(:clj
     (:import (org.postgresql.geometric PGpoint PGpolygon)
              (org.postgis PGgeometry MultiPolygon Polygon Point MultiLineString LineString
                           GeometryCollection Geometry MultiPoint)))
  (:require
    [harja.math :as math]
    [clojure.spec.alpha :as s]
    #?(:cljs
       [ol.proj :as ol-proj])))

(s/def ::single-coordinate (s/every number? :min-count 2 :max-count 2))
(s/def ::multiple-coordinates (s/every ::single-coordinate))
(s/def ::coordinates
  ;; :coordinates avainta käytetään joko yhden [x y] koordinaatin
  ;; tai [[x1 y1] ... [xN yN]] vektorin esittämiseen.
  (s/or :single-coordinate ::single-coordinate
        :multiple-coordinates ::multiple-coordinates))
(s/def ::points (s/every ::single-coordinate))

(defmulti geometria-spec
          "Määrittelee geometrian tyypin mukaisen specin"
          :type)

(s/def ::geometria
  (s/multi-spec geometria-spec :type))

(defmethod geometria-spec :point [_]
  (s/keys :req-un [::coordinates]))

(defmethod geometria-spec :geometry-collection [_]
  (s/keys :req-un [::geometries]))
(s/def ::geometries (s/every ::geometria))

(defmethod geometria-spec :multipolygon [_]
  (s/keys :req-un [::polygons]))
(s/def ::polygons (s/coll-of ::polygon))

(defmethod geometria-spec :polygon [_]
  ::polygon)
(s/def ::polygon (s/keys :req-un [::coordinates]))

(defmethod geometria-spec :multipoint [_]
  (s/keys :req-un [::coordinates]))

(defmethod geometria-spec :line [_]
  ::line)
(s/def ::line (s/keys :req-un [::points]))

(defmethod geometria-spec :multiline [_]
  (s/keys :req-un [::lines]))
(s/def ::lines (s/every ::line))


;; FIXME: Jossain vaiheessa geometriat ja featuret näyttää
;; menneen vähän sekaisin ja tarvitaan specit eri
;; suomenkielisille varianteille: viiva, moniviiva
;; sekä featureille: icon, merkki
;;
;; Nämä pitäisi korjata käyttöpaikoissa.

(defmethod geometria-spec :icon [_]
  (s/keys :req-un [::coordinates]))

(s/def ::radius pos?)

(defmethod geometria-spec :circle [_]
  (s/keys :req-un [::coordinates ::radius]))

(defmethod geometria-spec :moniviiva [_]
  (s/keys :req-un [::lines]))
(defmethod geometria-spec :viiva [_]
  ::line)
(defmethod geometria-spec :merkki [_]
  (s/keys :req-un [::coordinates]))




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

     MultiPoint
     (pg->clj [^MultiPoint mp]
       {:type :multipoint
        :coordinates (mapv pg->clj (.getPoints mp))})

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
   (defmethod clj->pg :multipoint [{c :coordinates :as mp}]
     (MultiPoint. (into-array Point
                              (map clj->pg c)))))

#?(:clj
   (defn geometry-collection [geometriat]
     (GeometryCollection. (into-array Geometry geometriat))))

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
   (defn muunna-reitti [{reitti :reitti :as rivi}]
     (if reitti
       (assoc rivi
         :reitti (pg->clj reitti))
       rivi)))

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
                 (org.locationtech.jts.geom.Coordinate. (first coordinate) (second coordinate))
                 nil euref->wgs84-transform)]
         [(.y c) (.x c)])
       (org.geotools.geometry.jts.JTS/transform coordinate nil euref->wgs84-transform))))

#?(:clj
   (def wgs84->euref-transform (org.geotools.referencing.CRS/findMathTransform wgs84 euref true)))

#?(:clj
   (defn wgs84->euref
     [coord]
     (let [c (org.geotools.geometry.jts.JTS/transform (org.locationtech.jts.geom.Coordinate. (:x coord) (:y coord))
                                                      nil wgs84->euref-transform)]
       {:x (.x c) :y (.y c)})))

(def +etrs-tm35fin+ "EPSG:3067")
(def +wgs84+ "EPSG:4326")

#?(:cljs
   (defn wgs84->etrsfin
     "Sijainti on [lon lat] vector."
     [sijainti]
     (ol-proj/transform (clj->js sijainti) +wgs84+ +etrs-tm35fin+)))

#?(:cljs
   (defn geolocation-api []
     (.-geolocation js/navigator)))

#?(:cljs
   (defn geolokaatio-tuettu? []
     (not (nil? (geolocation-api)))))

#?(:cljs
   (defn nykyinen-geolokaatio [sijainti-saatu-fn virhe-fn]
     (let [paikannusoptiot {:enableHighAccuracy true
                            :maximumAge 1000
                            :timeout 10000}]
       (.getCurrentPosition (geolocation-api)
                            sijainti-saatu-fn
                            virhe-fn
                            paikannusoptiot))))

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

(defn- muuta-valimatkaa* [funktio a b prosentti] [(funktio a (* prosentti (Math/abs (- a b)))) b])

(defn- kasvata-vasemmalle [[minx _ maxx _] prosentti]
  (first (muuta-valimatkaa* - minx maxx prosentti)))

(defn- kasvata-alaspain [[_ miny _ maxy] prosentti]
  (first (muuta-valimatkaa* - miny maxy prosentti)))

(defn- kasvata-oikealle [[minx _ maxx _] prosentti]
  (first (muuta-valimatkaa* + maxx minx prosentti)))

(defn- kasvata-ylospain [[_ miny _ maxy] prosentti]
  (first (muuta-valimatkaa* + maxy miny prosentti)))

(defn extent-pinta-ala [[minx miny maxx maxy]]
  (Math/abs (* (- maxx minx) (- maxy miny))))

(defn toisen-asteen-yhtalo [a b c]
  [(/ (+ (- b) (Math/sqrt (- (* b b) (* 4 a c)))) (* 2 a))
   (/ (- (- b) (Math/sqrt (- (* b b) (* 4 a c)))) (* 2 a))])

(defn laajenna-pinta-alaan [[minx miny maxx maxy :as ext] haluttu-ala]
  ;; Käytetään estämään liian lähelle zoomaamista
  (if (> haluttu-ala (extent-pinta-ala ext))
    (let [x-pituus (- maxx minx)
         y-pituus (- maxy miny)
         laajennos (/ (apply max (toisen-asteen-yhtalo
                                   1
                                   (+ x-pituus y-pituus)
                                   (- (* x-pituus y-pituus) haluttu-ala)))
                      2)]
     (laajenna-extent ext laajennos))

    ext))


(defn laajenna-extent-prosentilla
  ([extent] (laajenna-extent-prosentilla extent [0.001 0.001 0.001 0.05]))
  ([extent [vasen alas oikea ylos]]
   [(kasvata-vasemmalle extent vasen)
    (kasvata-alaspain extent alas)
    (kasvata-oikealle extent oikea)
    (kasvata-ylospain extent ylos)]))

(defn extent-keskipiste [[minx miny maxx maxy]]
  (let [width (- maxx minx)
        height (- maxy miny)]
    [(float (+ minx (/ width 2)))
     (float (+ miny (/ height 2)))]))

(defn yhdista-extent
  "Yhdistää kaksi annettua extentiä ja palauttaa uuden extentin,
  johon molemmat mahtuvat"
  ([] nil)
  ([[e1-minx e1-miny e1-maxx e1-maxy] [e2-minx e2-miny e2-maxx e2-maxy]]
   [(Math/min e1-minx e2-minx) (Math/min e1-miny e2-miny)
    (Math/max e1-maxx e2-maxx) (Math/max e1-maxy e2-maxy)]))

(defn pisteet
  "Palauttaa annetun geometrian pisteet sekvenssinä"
  [{type :type :as g}]
  (case type
    :line (:points g)
    :multiline (mapcat :points (:lines g))
    :polygon (:coordinates g)
    :multipolygon (mapcat :coordinates (:polygons g))
    :point [(:coordinates g)]
    :multipoint (:coordinates g)
    :icon [(:coordinates g)]
    :circle [(:coordinates g)]
    :viiva (:points g)
    :moniviiva (mapcat :points (:lines g))
    :merkki [(:coordinates g)]
    :geometry-collection (mapcat pisteet (:geometries g))))

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

(defmethod extent :line [geo]
  (laske-pisteiden-extent (pisteet geo)))

(defmethod extent :multiline [geo]
  (laske-pisteiden-extent (pisteet geo)))

;; Kuinka paljon yksittäisen pisteen extentiä laajennetaan joka suuntaan
(def pisteen-extent-laajennus 2000)

(defn- extent-point-circle [c]
  (let [d pisteen-extent-laajennus
        [[x y]] c]
    [(- x d) (- y d) (+ x d) (+ y d)]))

(defmethod extent :point [geo]
  (extent-point-circle (pisteet geo)))

(defmethod extent :circle [geo]
  (extent-point-circle (pisteet geo)))

(defmethod extent :icon [geo]
  (extent-point-circle (pisteet geo)))

(defmethod extent :merkki [geo]
  (extent-point-circle (pisteet geo)))

(defmethod extent :viiva [geo]
  (laske-pisteiden-extent (pisteet geo)))

(defmethod extent :moniviiva [geo]
  (laske-pisteiden-extent (pisteet geo)))

(defmethod extent :multipolygon [geo]
  (laske-pisteiden-extent (pisteet geo)))

(defmethod extent :polygon [geo]
  (laske-pisteiden-extent (pisteet geo)))

(defmethod extent :default [geo]
  (laske-pisteiden-extent (pisteet geo)))

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

(defn extent-sisalla?
  "Tarkistaa onko piste extentin sisällä. Ottaa sisään extentin [x1 y1 x2 y2] ja
pisteen [px py]."
  [[x1 y1 x2 y2] [px py]]
  (and (<= x1 px x2)
       (<= y1 py y2)))

(defn etaisyys [[x1 y1] [x2 y2]]
  (let [dx (- x2 x1)
        dy (- y2 y1)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn alueen-hypotenuusa
  "Laskee alueen hypotenuusan, jotta tiedetään minkä kokoista aluetta katsotaan."
  [{:keys [xmin ymin xmax ymax]}]
  (let [dx (- xmax xmin)
        dy (- ymax ymin)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn karkeistustoleranssi
  "Määrittelee reittien karkeistustoleranssin alueen koon mukaan."
  [alue]
  (let [pit (alueen-hypotenuusa alue)]
    (/ pit 200)))

(defn klikkaustoleranssi
  "Määrittelee klikattavan pisteen tarkkuustoleranssin extent koolle"
  [extent]
  (let [pit (extent-hypotenuusa extent)
        toleranssi (/ pit 200)]
    toleranssi))

(defn kulma
  "Palauttaa kahden pisteen välisen kulman radiaaneina"
  [[x1 y1] [x2 y2]]
  (let [dx (- x2 x1)
        dy (- y2 y1)]
    (Math/atan2 dy dx)))

(defn viivojen-paatepisteet-koskettavat-toisiaan? [viiva1 viiva2 threshold]
  (boolean
    (some (fn [sijainti1]
            (some (fn [sijainti2]
                    (<= (math/pisteiden-etaisyys sijainti1 sijainti2) threshold))
                  (:points viiva2)))
          (:points viiva1))))

(defprotocol IPiste
  (xy [this] "Palauttaa vektorin [x y]"))

(extend-protocol IPiste
  #?(:clj  clojure.lang.PersistentVector
     :cljs PersistentVector)
  (xy [this]
    this)
  #?(:clj  clojure.lang.PersistentArrayMap
     :cljs PersistentArrayMap)
  (xy [{:keys [x y]}]
    [x y]))
