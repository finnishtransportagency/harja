(ns harja.palvelin.palvelut.karttakuvat
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.params :refer [wrap-params]]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelu]]
            [harja.ui.kartta.esitettavat-asiat
             :refer [kartalla-esitettavaan-muotoon]]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.karttakuvat.piirto
             :refer [piirra-karttakuvaan]])
  (:import (java.awt.image BufferedImage)
           (java.awt Color BasicStroke RenderingHints)
           (java.awt.geom AffineTransform Line2D$Double)
           (javax.imageio ImageIO)))

(defprotocol KarttakuvaLahteet
  (rekisteroi-karttakuvan-lahde!
   [this nimi lahde-fn]
   "Rekisteröi karttakuvadatan lähteen. Funktio ottaa parametriksi käyttäjän
sekä HTTP request parametrit mäppinä ja palauttaa karttakuvaan piirrettävän
datan kartalla esitettävässä muodossa.")
  (poista-karttakuvan-lahde! [this nimi]))

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

     :resoluutio resoluutio

     :parametrit (dissoc parametrit
                         "x1" "y1" "x2" "y2" "r" "pr")}))


(defn- aseta-kuvan-koordinaatisto [g kuva extent]
  (.transform
   g
   (let [[w h] kuva
         [x1 y1 x2 y2] extent
         sx (/ 1 (/ (- x2 x1) w))
         sy (/ -1 (/ (- y2 y1) h))
         tx (- x1)
         ty (- y1)]
     (doto (AffineTransform.)
       (.translate 0 h)
       (.scale sx sy)
       (.translate tx ty)))))


(defn- luo-kuva [{:keys [extent resoluutio kuva] :as parametrit} asiat]
  (println "PARAMETRIT: " (pr-str parametrit))
  (let [[w h] (:kuva parametrit)
        img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (doto (.createGraphics img)
            (.addRenderingHints (RenderingHints.
                                 RenderingHints/KEY_ANTIALIASING
                                 RenderingHints/VALUE_ANTIALIAS_ON)))
        [x1 _ x2 _] extent]
    (aseta-kuvan-koordinaatisto g kuva extent)
    (piirra-karttakuvaan (/ (- x2 x1) w) g
                         asiat)

    ;;; TÄMÄN viivan pitäisi menna vasen ala nurkasta oikea ylä nurkkaan
    #_(.drawLine g (nth extent 0) (nth extent 1) (nth extent 2) (nth extent 3))
    img))

(defn- hae-karttakuvadata
  "Hakee karttakuvadatan oikeasti lähteestä"
  [lahteet user parametrit]
  (let [lahteen-nimi (keyword (get-in parametrit [:parametrit "_"]))
        lahde (get lahteet lahteen-nimi)]
    (when lahde
      (lahde user parametrit))))

(defn karttakuva [lahteet user parametrit]
  (println "PARAM: " (pr-str parametrit))
  (let [kuva (->> parametrit lue-parametrit
                  (hae-karttakuvadata lahteet user)
                  (luo-kuva parametrit)
                  kirjoita-kuva)]
    {:status 200
     :headers {"Content-Type" "image/png"
               "Content-Length" (count kuva)
               "Access-Control-Allow-Origin" "*"}
     :body (java.io.ByteArrayInputStream. kuva)}))

(defrecord Karttakuvat [lahteet]
  component/Lifecycle
  (start [{db :db
           http :http-palvelin
           :as this}]
    (julkaise-palvelu
     http :karttakuva
     (wrap-params (fn [req]
                    (karttakuva @lahteet (:kayttaja req) (:params req))))
     {:ring-kasittelija? true})
    this)

  (stop [{http :http-palvelin
          :as this}]
    (poista-palvelu http :karttakuva)
    this)

  KarttakuvaLahteet
  (rekisteroi-karttakuvan-lahde! [this nimi lahde-fn]
    (swap! lahteet assoc nimi lahde-fn))
  (poista-karttakuvan-lahde! [this nimi]
    (swap! lahteet dissoc nimi)))

(defn luo-karttakuvat []
  (->Karttakuvat (atom {})))
