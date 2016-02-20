(ns harja.palvelin.palvelut.karttakuvat
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.params :refer [wrap-params]]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelu]])
  (:import (java.awt.image BufferedImage)
           (java.awt Color BasicStroke)
           (java.awt.geom AffineTransform)
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
  (:toteumat
   (harja.palvelin.palvelut.tilannekuva/hae-tilannekuvaan
    db user {:talvi {"pinnan tasaus" true,
                     "lumivallien madaltaminen" true,
                     "aurausviitoitus ja kinostimet" true,
                     "suolaus" true,
                     "muu" true,
                     "sulamisveden haittojen torjunta" true,
                     "linjahiekoitus" true,
                     "lumensiirto" true,
                     "liuossuolaus" true,
                     "auraus ja sohjonpoisto" true,
                     "pistehiekoitus" true,
                     "paannejaan poisto" true},
             :urakka-id nil,
             :turvallisuus {:turvallisuuspoikkeamat false}
             :laatupoikkeamat {:tilaaja false, :urakoitsija false, :konsultti false}
             :kesa {"pinnan tasaus" false,
                    "paallysteiden juotostyot" false,
                    "sorateiden polynsidonta" false,
                    "harjaus" false,
                    "l- ja p-alueiden puhdistus" false,
                    "muu" false,
                    "koneellinen vesakonraivaus" false,
                    "paallysteiden paikkaus" false,
                    "koneellinen niitto" false,
                    "siltojen puhdistus" false,
                    "liikennemerkkien puhdistus" false,
                    "sorastus" false,
                    "sorateiden tasaus" false,
                    "sorateiden muokkaushoylays" false},
             :alue {:xmin -906240, :ymin 6829056, :xmax 1995776, :ymax 7654400}
             :ilmoitukset {:tyypit {:toimenpidepyynto false,
                                    :kysely false, :tiedoitus false}
                           :tilat #{:avoimet}}
             :yllapito {:paallystys false, :paikkaus false}
             :hallintayksikko 9
             :urakoitsija nil
             :tarkastukset {:tiesto false, :talvihoito false, :soratie false, :laatu false, :pistokoe false}
             :alku #inst "2016-02-13T06:55:39.000-00:00"
             :nykytilanne? true
             :loppu #inst "2016-02-20T06:55:39.000-00:00" :urakkatyyppi :hoito})))



;; FIXME: tämä pitää lopulta refaktoroida siten, että
;; näytettävät asiat voi rekisteröidä jotenkin. Tämän ns:n ei
;; pidä tehdä tietokantakyselyjä tai päätellä mitä tietoa haetaan
;; näytettäväksi


(defmulti piirra (fn [_ reitti] (:type reitti)))
(defmethod piirra :multiline [g multiline]
  (doseq [l (:lines multiline)]
    (piirra g l)))

(defmethod piirra :line [g {points :points :as line}]
  (doseq [[[x1 y1] [x2 y2]] (partition 2 1 points)]
    (println "VIIVA: " x1 "," x2 "  ->  " x2 "," y2)
    (.drawLine g x1 y1 x2 y2)))

(defn- luo-kuva [{:keys [extent resoluutio kuva] :as parametrit} db user]
  (println "PARAMETRIT: " (pr-str parametrit))
  (let [[w h] (:kuva parametrit)
        img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics img)]
    (.setColor g (Color. 1.0 0.0 0.0 1.0))
    (.setStroke g (BasicStroke. 30))
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
    (doseq [t (toteumat db user)]
      (piirra g (:reitti t)))

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


