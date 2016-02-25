(ns harja.palvelin.palvelut.karttakuvat
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.params :refer [wrap-params]]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelu]]
            [harja.palvelin.palvelut.tilannekuva :as tilannekuva]
            [harja.ui.kartta.esitettavat-asiat
             :refer [kartalla-esitettavaan-muotoon]]
            [taoensso.timbre :as log])
  (:import (java.awt.image BufferedImage)
           (java.awt Color BasicStroke RenderingHints)
           (java.awt.geom AffineTransform Line2D$Double)
           (javax.imageio ImageIO)))

(defn- kirjoita-kuva [kuva]
  (let [out (java.io.ByteArrayOutputStream.)]
    (ImageIO/write kuva "png" out)
    (.close out)
    (.toByteArray out)))

(defn- lue-numero [parametrit avain]
  (-> parametrit (get avain) (Double/parseDouble)))

(defn- lue-parametrit [parametrit]
  (let [[x1 y1 x2 y2 resoluutio pixel-ratio]
        (map (partial lue-numero parametrit)
             ["x1" "y1" "x2" "y2" "r" "pr"])
        dx (Math/abs (- x2 x1))
        dy (Math/abs (- y2 y1))]
    {;; tuotettavan kuvan koko
     :kuva [(Math/floor (/ dx resoluutio)) (Math/floor (/ dy resoluutio))]

     ;; kartta-alue, jolle kuva tuotetaan
     :extent [x1 y1 x2 y2]

     :resoluutio resoluutio}))

(defn toteumat [db user]
  (kartalla-esitettavaan-muotoon
   (:toteumat
    (tilannekuva/hae-tilannekuvaan
     db user {:talvi #{20 24 39 21 40 41 17 23 19 38 18 42},
              :urakka-id nil,
              :turvallisuus {:turvallisuuspoikkeamat false}
              :laatupoikkeamat {:tilaaja false, :urakoitsija false, :konsultti false}
              :kesa #{},
              :alue {:xmin -906240, :ymin 6829056, :xmax 1995776, :ymax 7654400}
              :hallintayksikko 9
              :urakoitsija nil
              :alku #inst "2016-02-13T06:55:39.000-00:00"
              :nykytilanne? true
              :loppu #inst "2016-02-20T06:55:39.000-00:00" :urakkatyyppi :hoito}))
   nil nil
   (map #(assoc % :tyyppi-kartalla :toteuma))))



;; FIXME: tämä pitää lopulta refaktoroida siten, että
;; näytettävät asiat voi rekisteröidä jotenkin. Tämän ns:n ei
;; pidä tehdä tietokantakyselyjä tai päätellä mitä tietoa haetaan
;; näytettäväksi
;;
;; Lisäksi esitettävät asiat, värit, ulkoasu jne .cljs namespacet siirrettävä
;; .cljc muotoon
;;
;; Tämä nimiavaruus hoitaa perus dispatch URL parametreistä oikean tiedon
;; luokse ja kutsuu harja.palvelin.palvelut.karttakuvat.piirto
;; namespacea, jonne implementoidaan renderöinti, joka tekee saman kuin
;; openlayers featuret namespacen luo-feature (mutta kuvaksi).

(defmulti piirra (fn [_ toteuma reitti] (:type reitti)))

(defmethod piirra :multiline [g toteuma multiline]
  (println "TOTEUMA: " toteuma)
  (doseq [l (:lines multiline)]
    (piirra g toteuma l)))

(defmethod piirra :line [g toteuma {points :points :as line}]

  (doseq [[[x1 y1] [x2 y2]] (partition 2 1 points)
          :let [line (Line2D$Double.  x1 y1 x2 y2)]]
    (.draw g line)))

(defn px [img-width extent-width pikselit]
  (* (/ extent-width img-width) pikselit))

(defn- luo-kuva [{:keys [extent resoluutio kuva] :as parametrit} db user]
  (println "PARAMETRIT: " (pr-str parametrit))
  (let [[w h] (:kuva parametrit)
        img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (doto (.createGraphics img)
            (.addRenderingHints (RenderingHints.
                                 RenderingHints/KEY_ANTIALIASING
                                 RenderingHints/VALUE_ANTIALIAS_ON)))
        [x1 _ x2 _] extent
        px (partial px w (- x2 x1))]
    (.setColor g (Color. 1.0 0.0 0.0 1.0))
    (.setStroke g (BasicStroke. (px 3)
                                BasicStroke/CAP_ROUND
                                BasicStroke/JOIN_MITER))

    (.transform
     g
     (let [[w h] kuva
           [x1 y1 x2 y2] extent
           sx (/ 1 (/ (- x2 x1) w))
           sy (/ -1 (/ (- y2 y1) h))
           tx (- x1)
           ty (- y1)]
       (println "SCALE: "  sx sy)
       (doto (AffineTransform.)
         (.translate 0 h)
         (.scale sx sy)
         (.translate tx ty))))
    (doseq [{reitti :reitti :as toteuma} (toteumat db user)
            :when reitti]
      (piirra g toteuma reitti))

    ;;; TÄMÄN viivan pitäisi menna vasen ala nurkasta oikea ylä nurkkaan
    #_(.drawLine g (nth extent 0) (nth extent 1) (nth extent 2) (nth extent 3))
    img))

(defn karttakuva [db user parametrit]
  (println "PARAM: " (pr-str parametrit))
  (let [kuva (-> parametrit lue-parametrit
                 (luo-kuva db user)
                 kirjoita-kuva)]
    {:status 200
     :headers {"Content-Type" "image/png"
               "Content-Length" (count kuva)
               "Access-Control-Allow-Origin" "*"}
     :body (java.io.ByteArrayInputStream. kuva)
     }))

(defrecord Karttakuvat []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin
           :as this}]
    (julkaise-palvelu
     http :karttakuva
     (wrap-params (fn [req]
                    (karttakuva db (:kayttaja req) (:params req))))
     {:ring-kasittelija? true})
    this)

  (stop [{http :http-palvelin
          :as this}]
    (poista-palvelu http :karttakuva)
    this))
