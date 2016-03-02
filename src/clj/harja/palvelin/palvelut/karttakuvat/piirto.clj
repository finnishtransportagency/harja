(ns harja.palvelin.palvelut.karttakuvat.piirto
  "Hoitaa karttalla esitettävien asioiden piirtämisen Java Graphics2D
  piirtoalustaan."
  (:import (java.awt Color BasicStroke RenderingHints)
           (java.awt.geom AffineTransform Line2D$Double)
           (javax.imageio ImageIO))
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

(defmacro with-rotation [g anchor-x anchor-y rad & body]
  `(let [at# (.getTransform ~g)]
     (.rotate ~g ~rad ~anchor-x ~anchor-y)
     ~@body
     (.setTransform ~g at#)))

;; Yksinkertainen kuvien cache
(def kuvat (atom {}))
(defn hae-kuva [tiedosto]
  (swap! kuvat
         (fn [kuvat]
           (if (contains? kuvat tiedosto)
             kuvat
             (assoc kuvat tiedosto
                    (ImageIO/read
                     (ClassLoader/getSystemResourceAsStream tiedosto))))))
  (if-let [kuva (get @kuvat tiedosto)]
    kuva
    (do (log/warn "Karttakuvaa " tiedosto " ei voitu ladata!")
        nil)))

(def ^:private
  ;; Rajapinnan tarvima ImageObserver, joka ei tee mitään
  nil-image-observer (reify java.awt.image.ImageObserver
                       (imageUpdate [this img flags x y width height])))

(def ^{:doc "Ikonien tiheys, välimatkaksi otetaan alueen hypotenuusa jaettuna tällä.
Kasvata arvoa, jos haluat tiheämmin näkyvät ikonit."
       :private true}
  ikonien-tiheys 15)

(defn- piirra-ikonit [g {points :points ikonit :ikonit}]
  (let [segmentit (partition 2 1 points)
        valimatka (/ (geo/extent-hypotenuusa *extent*) ikonien-tiheys)
        paikat (apurit/taitokset-valimatkoin valimatka
                                             (apurit/pisteiden-taitokset points))]
    (doseq [[[x y] rotaatio] paikat]
      (with-rotation g x y rotaatio
        (doseq [{:keys [img scale]} ikonit
                :let [kuva (hae-kuva img)]]
          (when kuva
            (.drawImage g kuva
                        (doto (AffineTransform.)
                          ;; Keskitetään kuva
                          (.translate  (px (- (/ (.getWidth kuva) 2)))
                                      (px (- (/ (.getHeight kuva) 2))))
                          ;; Siirretään kuvan kohtaan
                          (.translate x y)

                          ;; Skaalataan pikselit karttakoordinaateiksi
                          (.scale (px scale) (px scale)))
                        nil-image-observer)))))))


(defmethod piirra :viiva [g toteuma {:keys [viivat points ikonit] :as alue}]
  (let [viivat (reverse (sort-by :width viivat))]
    (doseq [viiva viivat]
      (piirra-viiva g  alue viiva))
    (piirra-ikonit g alue)))

(defn piirra-karttakuvaan [extent px-scale g asiat]
  (binding [*px-scale* px-scale
            *extent* extent]
    (doseq [{alue :alue :as asia} asiat
            :when alue]
      (piirra g asia alue))))
