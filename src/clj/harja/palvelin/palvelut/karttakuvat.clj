(ns harja.palvelin.palvelut.karttakuvat
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.params :refer [wrap-params]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]])
  (:import (java.awt.image BufferedImage)
           (java.awt Color BasicStroke)
           (javax.imageio ImageIO)))

(defn- kirjoita-kuva [kuva]
  (let [out (java.io.ByteArrayOutputStream.)]
    (ImageIO/write kuva "png" out)
    (.close out)
    (.toByteArray out)))

(defn- lue-numero [parametrit avain]
  (-> parametrit (get avain) (Double/parseDouble)))

(defn- lue-parametrit [parametrit]
  (let [[x1 y1 x2 y2 resoluutio pixel-ratio] (map (partial lue-numero parametrit)
                                                  ["x1" "y1" "x2" "y2" "r" "pr"])
        dx (Math/abs (- x2 x1))
        dy (Math/abs (- y2 y1))]
    {;; tuotettavan kuvan koko
     :kuva [(Math/floor (/ dx resoluutio)) (Math/floor (/ dy resoluutio))]

     ;; kartta-alue, jolle kuva tuotetaan
     :extent [x1 y1 x2 y2]

     :resoluutio resoluutio}))

(defn- luo-kuva [parametrit]
  (println "PARAMETRIT: " (pr-str parametrit))
  (let [[w h] (:kuva parametrit)
        img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics img)]
    (.setColor g (Color. 1.0 0.0 0.0 1.0))
    (.setStroke g (BasicStroke. 30))
    (.drawLine g 0 0 w h)
    img))

(defn karttakuva [db user parametrit]
  (println "PARAM: " (pr-str parametrit))
  (let [kuva (-> parametrit lue-parametrit luo-kuva kirjoita-kuva)]
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


