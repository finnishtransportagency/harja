(ns harja.geo
  "Yleisiä geometria-apureita"
  (:require [harja.loki :refer [log]]))


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

(defn- pisteet
  "Palauttaa annetun geometrian pisteet sekvenssinä"
  [{type :type :as g}]
  (case type
    :line (:points g)
    :arrow-line (:points g)
    :multiline (mapcat :points (:lines g))
    :polygon (:coordinates g)
    :multipolygon (mapcat :coordinates (:polygons g))
    :point [(:coordinates g)]
    :circle [(:coordinates g)]))

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
(def pisteen-extent-laajennus 350)

(defn- extent-point-circle [c]
  (let [d pisteen-extent-laajennus
        [x y] c]
    [(- x d) (- y d) (+ x d) (+ y d)]))

(defmethod extent :point [{c :coordinates}]
  (extent-point-circle c))

(defmethod extent :circle [{c :coordinates}]
  (extent-point-circle c))

(defmethod extent :multipolygon [{polygons :polygons}]
  (laske-pisteiden-extent (mapcat :coordinates polygons)))

(defmethod extent :polygon [{coordinates :coordinates}]
  (laske-pisteiden-extent coordinates))

(defmethod extent :arrow-line [{points :points}]
  (laske-pisteiden-extent points))
  
(defn extent-monelle [geometriat]
  (laske-pisteiden-extent (mapcat pisteet geometriat)))


  
;; Päättelee annetulle geometrialle hyvän ikonisijainnin
;; geometry -> [x y]
(defmulti ikonin-sijainti (fn [geometry] (:type geometry)))

(defmethod ikonin-sijainti :point [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :default [g]
  (keskipiste g))


