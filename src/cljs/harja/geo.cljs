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

(defn extent-keskipiste [[minx miny maxx maxy]]
  (let [width (- maxx minx)
        height (- maxy miny)]
    [(+ minx (/ width 2))
     (+ miny (/ height 2))]))

(defn yhdista-extent
  "Yhdistää kaksi annettua extentiä ja palauttaa uuden extentin, johon molemmat mahtuvat"
  ([] nil)
  ([[e1-minx e1-miny e1-maxx e1-maxy] [e2-minx e2-miny e2-maxx e2-maxy]]
   [(Math/min e1-minx e2-minx) (Math/min e1-miny e2-miny)
    (Math/max e1-maxx e2-maxx) (Math/max e1-maxy e2-maxy)]))

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
    :icon [(:coordinates g)]
    :tack-icon [(:coordinates g)]
    :tack-icon-line (:points g)
    :sticker-icon [(:coordinates g)]
    :sticker-icon-line (:points g)
    :circle [(:coordinates g)]))

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
         (log "LASKE-EXTENT-XF: " (pr-str input)) ; FIXME: poista kun kaikki käyttävät kartalla esitettävään muotoon paradigmaa
         (loop [minx- @minx
                miny- @miny
                maxx- @maxx
                maxy- @maxy
                [[x y] & pisteet] (pisteet (:alue input))]
           (if-not x
             (do (vreset! minx minx-)
                 (vreset! miny miny-)
                 (vreset! maxx maxx-)
                 (vreset! maxy maxy-))
             (if minx-
               (recur (Math/min minx- x) (Math/min miny- y)
                      (Math/max maxx- x) (Math/max maxy- y)
                      pisteet)
               (recur x y x y pisteet))))
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

(defmethod extent :tack-icon [{c :coordinates}]
  (extent-point-circle c))

(defmethod extent :tack-icon-line [{points :points}]
  (laske-pisteiden-extent points))

(defmethod extent :sticker-icon [{c :coordinates}]
  (extent-point-circle c))

(defmethod extent :sticker-icon-line [{points :points}]
  (laske-pisteiden-extent points))

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

(defmethod ikonin-sijainti :circle [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :icon [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :tack-icon [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :sticker-icon [geom]
  (:coordinates geom))

(defmethod ikonin-sijainti :default [g]
  (keskipiste g))


