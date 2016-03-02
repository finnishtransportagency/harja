(ns harja.palvelin.palvelut.karttakuvat.piirto
  "Hoitaa karttalla esitettävien asioiden piirtämisen Java Graphics2D
  piirtoalustaan."
  (:import (java.awt Color BasicStroke RenderingHints)
           (java.awt.geom AffineTransform Line2D$Double)))

(def ^:dynamic *px-scale* 1)

(defn px [pikselit]
  (* *px-scale* pikselit))

(defmulti piirra (fn [_ toteuma alue] (:type alue)))

(defn- aseta-viiva-tyyli [g {:keys [color width dash cap join miter]}]
  (println "COL: " color "; STROKE:  " width " => " (px width))
  (.setColor g  color)
  (.setStroke g (BasicStroke. (px width)
                              BasicStroke/CAP_ROUND
                              BasicStroke/JOIN_MITER)))

(defmethod piirra :viiva [g toteuma {:keys [viivat points ikonit :as viiva]}]
  (doseq [viiva viivat]
    (aseta-viiva-tyyli g viiva)
    (doseq [[[x1 y1] [x2 y2]] (partition 2 1 points)
            :let [line (Line2D$Double.  x1 y1 x2 y2)]]
      (println "PIIRRÄ " x1 "," y1 "  =>  " x2 "," y2)
      (.draw g line))))

(defn piirra-karttakuvaan [px-scale g asiat]
  (binding [*px-scale* px-scale]
    (doseq [{alue :alue :as asia} asiat
            :when alue]
      (piirra g asia alue))))
