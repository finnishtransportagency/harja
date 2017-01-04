(ns harja.palvelin.palvelut.karttakuvat
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.params :refer [wrap-params]]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelut]]
            [harja.ui.kartta.esitettavat-asiat
             :refer [kartalla-esitettavaan-muotoon]]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.karttakuvat.piirto
             :refer [piirra-karttakuvaan]]
            [harja.transit :as transit]
            [harja.geo :as geo])
  (:import (java.awt.image BufferedImage)
           (java.awt Color BasicStroke RenderingHints)
           (java.awt.geom AffineTransform Line2D$Double)
           (javax.imageio ImageIO)))

(defprotocol KarttakuvaLahteet
  (rekisteroi-karttakuvan-lahde!
    [this nimi lahde-fn asiat-fn transit-parametri-nimi]
    "Rekisteröi karttakuvadatan sekä siihen liittyvien asioiden lähteen.
    Funktio ottaa parametriksi käyttäjän sekä HTTP request parametrit mäppinä ja
    palauttaa karttakuvaan piirrettävän datan kartalla esitettävässä muodossa.
    Toinen funktio ottaa samat parametrit ja lisäksi klikatun pisteen ja palauttaa
    kuvauksen kartalla löytyneistä asioista.
    Jos rekisteröinnissä on annettu transit parametrin nimi, luetaan se parametri
    clojure dataksi ja yhdistetään muihin parametreihin ennen funktion kutsua.")
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
       ;; Siirrytään alakulmaan
       (.translate 0 h)

       ;; Skaalaataan karttakoordinaatit pikseleiksi
       ;; ja käännetään Y-akseli
       (.scale sx sy)

       ;; Siirrytään kartan [x1 y1] kohtaan
       (.translate tx ty)))))


(defn- luo-kuva [{:keys [extent resoluutio kuva] :as parametrit} asiat]
  (try
    (let [[w h] (:kuva parametrit)
          img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
          g (doto (.createGraphics img)
              (.addRenderingHints (RenderingHints.
                                   RenderingHints/KEY_ANTIALIASING
                                   RenderingHints/VALUE_ANTIALIAS_ON)))
          [x1 _ x2 _] extent]
      (aseta-kuvan-koordinaatisto g kuva extent)
      (piirra-karttakuvaan extent [w h] (/ (- x2 x1) w) g asiat)

    ;;; TÄMÄN viivan pitäisi menna vasen ala nurkasta oikea ylä nurkkaan
                                        ;(.drawLine g (nth extent 0) (nth extent 1) (nth extent 2) (nth extent 3))
      img)
    (catch Throwable t
      (log/error t "Karttakuvan luonnissa poikkeus"))))

(defn- hae-karttakuvadata
  "Hakee karttakuvadatan oikeasti lähteestä"
  [lahteet user parametrit]
  (let [lahteen-nimi (keyword (get-in parametrit [:parametrit "_"]))
        {lahde :kuva transit-parametri :transit-parametri} (get lahteet lahteen-nimi)
        parametrit (as-> parametrit p

                     ;; Käännä yla/ala extentissä, koska ol taso ilmoittaa sen
                     ;; sen toisin päin kuin meillä
                     (assoc p
                            :extent (let [[vasen yla oikea ala] (:extent parametrit)]
                                      [vasen ala oikea yla]))

                     ;; Poistetaan "_" ja transit-parametri
                     (update p :parametrit dissoc "_" transit-parametri)

                     ;; Yhdistetään transit parametrista luetut arvot
                     (update p :parametrit merge
                             (when transit-parametri
                               (some-> parametrit
                                       (get-in [:parametrit transit-parametri])
                                       transit/lue-transit-string))))
        karttakuvadata (when lahde
                         (lahde user parametrit))]
    karttakuvadata))

(defn karttakuva [lahteet user parametrit]
  (let [parametrit (lue-parametrit parametrit)
        karttakuvadata (hae-karttakuvadata lahteet user parametrit)
        kuva (kirjoita-kuva
              (luo-kuva parametrit karttakuvadata))]
    {:status  200
     :headers {"cache-control"               "private, max-age=300"
               "Content-Type"                "image/png"
               "Content-Length"              (count kuva)
               "Access-Control-Allow-Origin" "*"}
     :body    (java.io.ByteArrayInputStream. kuva)}))

(defn karttakuva-asiat [lahteet user {:keys [parametrit koordinaatti extent]}]
  (let [lahteen-nimi (keyword (get parametrit "_"))
        {lahde :asiat transit-parametri :transit-parametri} (get lahteet lahteen-nimi)]
    (if lahde
      (lahde user
             (as-> parametrit p
               (assoc p :x (first koordinaatti) :y (second koordinaatti))
               (assoc p :toleranssi (geo/klikkaustoleranssi extent))
               (dissoc p transit-parametri)
               (merge p
                      (when transit-parametri
                        (some-> parametrit (get transit-parametri)
                                java.net.URLDecoder/decode
                                transit/lue-transit-string)))))
      (do
        (log/info "Yritettiin hakea karttakuvan asioita tuntemattomalle lähteelle: " lahteen-nimi)
        []))))

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
    (julkaise-palvelu http :karttakuva-klikkaus
                      (fn [user payload]
                        (karttakuva-asiat @lahteet user payload)))
    this)

  (stop [{http :http-palvelin
          :as this}]
    (poista-palvelut http :karttakuva :karttakuva-klikkaus)
    this)

  KarttakuvaLahteet
  (rekisteroi-karttakuvan-lahde! [this nimi lahde-fn asiat-fn transit-parametri-nimi]
    (swap! lahteet assoc nimi {:kuva lahde-fn
                               :asiat asiat-fn
                               :transit-parametri transit-parametri-nimi}))
  (poista-karttakuvan-lahde! [this nimi]
    (swap! lahteet dissoc nimi)))

(defn luo-karttakuvat []
  (->Karttakuvat (atom {})))
