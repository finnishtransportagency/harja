(ns harja.palvelin.palvelut.karttakuvat.piirto
  "Hoitaa karttalla esitettävien asioiden piirtämisen Java Graphics2D
  piirtoalustaan."
  (:import (java.awt Color BasicStroke RenderingHints Font)
           (java.awt.geom AffineTransform Line2D$Double)
           (javax.imageio ImageIO))
  (:require [harja.geo :as geo]
            [taoensso.timbre :as log]
            [harja.ui.kartta.apurit :as apurit]
            [harja.palvelin.palvelut.karttakuvat.ruudukko :as ruudukko]
            [harja.tyokalut.makrot :refer [go-loop-timeout]]
            [clojure.core.async :as async]))

(def ^:dynamic *px-scale* 1)
(def ^:dynamic *extent* nil)

(defn px [pikselit]
  (* *px-scale* pikselit))

(defmulti piirra (fn [_ toteuma alue ruudukko] (:type alue)))

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

(defmacro save-transform [g & body]
  `(let [at# (.getTransform ~g)]
     ~@body
     (.setTransform ~g at#)))

(defmacro with-rotation [g anchor-x anchor-y rad & body]
  `(save-transform
    ~g
    (.rotate ~g ~rad ~anchor-x ~anchor-y)
    ~@body))

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
  ikonien-tiheys 10)

(def ^{:doc "Raja, jota suuremmalla näkyvällä alueella ei enää piirretä ikoneita"
       :private true}
  ikonien-piirtoraja-m 1400000)

(defn- nuolten-paikat [valimatka taitokset paikka]
  (case paikka
    :alku
    (let [{:keys [sijainti rotaatio]} (first taitokset)]
      [[(first sijainti) rotaatio]])

    :loppu
    (let [{:keys [sijainti rotaatio]} (last taitokset)]
      [[(second sijainti) rotaatio]])

    :taitokset
    (apurit/taitokset-valimatkoin valimatka (butlast taitokset))))

(defn- piirra-kuva
  ([g kuva skaala x y]
   (piirra-kuva g kuva skaala x y 0.5 0.5))
  ([g kuva skaala x y x-anchor y-anchor]
   (.drawImage g kuva
               (doto (AffineTransform.)
                 ;; Keskitetään kuva
                 (.translate  (px (- (* (* skaala (.getWidth kuva)) x-anchor)))
                              (px (- (* (* skaala (.getHeight kuva)) y-anchor))))
                 ;; Siirretään kuvan kohtaan
                 (.translate x y)

                 ;; Skaalataan pikselit karttakoordinaateiksi
                 (.scale (px skaala) (px skaala)))
               nil-image-observer)))

(defn- piirra-ikonit [g {points :points ikonit :ikonit} ruudukko]
  (let [hypotenuusa (geo/extent-hypotenuusa *extent*)
        valimatka (/ hypotenuusa ikonien-tiheys)
        taitokset (apurit/pisteiden-taitokset points)
        ikonin-skaala (partial apurit/ikonin-skaala hypotenuusa)]
    (when (< hypotenuusa ikonien-piirtoraja-m)
      (doseq [{:keys [img scale paikka]} ikonit
              :let [paikat (mapcat (partial nuolten-paikat valimatka taitokset)
                                   paikka)
                    kuva (and img (hae-kuva img))
                    skaala (ikonin-skaala scale)]]
        (when kuva
          (doseq [[[x y] rotaatio] paikat]
            (when (not (ruudukko/kohta-asetettu? ruudukko x y))
              (ruudukko/aseta-kohta! ruudukko x y)
              (with-rotation g x y rotaatio
                (piirra-kuva g kuva skaala x y)))))))))

;;{:scale 1, :img public/images/tuplarajat/pinnit/pinni-punainen.png, :type :merkki, :coordinates (429739.8163550331 7206534.971915511)}
(defmethod piirra :merkki [g toteuma {:keys [scale img coordinates]} ruudukko]
  (when-let [kuva (hae-kuva img)]
    (let [[x y] coordinates]
      (with-rotation g x y Math/PI
        (piirra-kuva g kuva scale x y 0.5 1)))))

(defmethod piirra :viiva [g toteuma {:keys [viivat points ikonit] :as alue} ruudukko]
  (let [viivat (reverse (sort-by :width viivat))]
    (doseq [viiva viivat]
      (piirra-viiva g  alue viiva))
    (piirra-ikonit g alue ruudukko)))

(defmethod piirra :moniviiva [g toteuma {:keys [lines viivat ikonit] :as alue} ruudukko]
  (let [viivat (reverse (sort-by :width viivat))]
    (doseq [viiva viivat
            line lines]
      (piirra-viiva g line viiva)
      (piirra-ikonit g {:points (:points line)
                        :ikonit ikonit} ruudukko))))

(def varoitusteksti
  "Paljon tuloksia, kaikkea ei ehditty piirtää! Tarkenna hakuehtoja tai zoomaa lähemmäs.")

(def varoituskuva "public/images/varoitus.png")

(defn piirra-varoitus [g [w h] teksti]
  (save-transform
   g
   (.setTransform g (java.awt.geom.AffineTransform.))
   (let [fm (.getFontMetrics g)
         width (.stringWidth fm teksti)]
     (.setFont g (Font. "Dialog" Font/PLAIN 14))
     (let [x (float (- (/ w 2) (/ width 2)))
           y (float (- h 10))]
       (.setColor g Color/WHITE)
       (.fillRect g (int (- x 32)) (int (- y 18))
                  (+ width 120) 24)
       (.drawImage g (hae-kuva varoituskuva)
                   (int (- x 30)) (int (- y 18))
                   nil-image-observer)
       (.setColor g Color/BLACK)
       (.drawString g teksti x y)))))

(def piirron-aikakatkaisu-ms 10000)

(defn piirra-karttakuvaan [extent koko px-scale g asiat]
  (binding [*px-scale* px-scale
            *extent* extent]
    (let [ruudukko (ruudukko/ruudukko extent px-scale 128)
          ch (if (coll? asiat)
               (async/to-chan asiat)
               asiat)]
      (go-loop-timeout
       {:timeout piirron-aikakatkaisu-ms
        :on-timed-out (piirra-varoitus g koko varoitusteksti)}
       [{alue :alue :as asia} ch]
       (piirra g asia alue ruudukko)))))
