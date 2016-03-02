(ns harja.palvelin.palvelut.karttakuvat.piirto
  "Hoitaa karttalla esitettävien asioiden piirtämisen Java Graphics2D
  piirtoalustaan."
  (:import (java.awt Color BasicStroke RenderingHints)
           (java.awt.geom AffineTransform Line2D$Double))
  (:require [harja.geo :as geo]
            [taoensso.timbre :as log]
            [harja.ui.kartta.apurit :as apurit]))

(def ^:dynamic *px-scale* 1)
(def ^:dynamic *extent* nil)

(defn px [pikselit]
  (* *px-scale* pikselit))

(defmulti piirra (fn [_ toteuma alue] (:type alue)))

(defn- aseta-viiva-tyyli [g {:keys [color width dash cap join miter]}]
  ;;(println "COL: " color "; STROKE:  " width " => " (px width))
  (.setColor g  color)
  (.setStroke g (BasicStroke. (px width)
                              BasicStroke/CAP_ROUND
                              BasicStroke/JOIN_MITER)))

(defn- piirra-viiva [g {points :points} viiva]
  (aseta-viiva-tyyli g viiva)
  (let [segmentit (partition 2 1 points)]
    (doseq [[[x1 y1] [x2 y2]] segmentit
            :let [line (Line2D$Double.  x1 y1 x2 y2)]]
      (.draw g line))))

(def nuolen-kulma (* 0.25 Math/PI))

(defmacro with-rotation [g anchor-x anchor-y rad & body]
  `(let [at# (.getTransform ~g)]
     (.rotate ~g ~rad ~anchor-x ~anchor-y)
     ~@body
     (.setTransform ~g at#)))

(defn- piirra-ikonit [g {points :points ikonit :ikonit}]
  (log/debug "IKONIT: " (pr-str ikonit))
  (let [segmentit (partition 2 1 points)
        paikat (apurit/taitokset-valimatkoin 3000 ; FIXME: constant sama kuin frontilla
                                             (apurit/pisteiden-taitokset points))]
    (println "TAITOKSET PAIKOISSA: " (pr-str paikat))
    (doseq [[{:keys [sijainti rotaatio]} & taitokset] paikat
            :let [[x y] sijainti]]
      (with-rotation g x y rotaatio
        (.drawImage Line g x1 y1 (+ x1 (px 20)) (+ y1 (px 50))))
      (with-rotation g x1 y1 (- kulma nuolen-kulma)
        (.drawLine g x1 y1 (+ x1 (px 20)) (+ y1 (px 50))))
      )))


(defmethod piirra :viiva [g toteuma {:keys [viivat points ikonit] :as alue}]
  (let [viivat (reverse (sort-by :width viivat))]
    (piirra-ikonit g alue)
    (doseq [viiva viivat]
      (piirra-viiva g  alue viiva))))

(defn piirra-karttakuvaan [extent px-scale g asiat]
  (binding [*px-scale* px-scale
            *extent* extent]
    (doseq [{alue :alue :as asia} asiat
            :when alue]
      (piirra g asia alue))))
