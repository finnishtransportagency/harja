(ns harja-laadunseuranta.paikannus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.kalman :as kalman]
            [harja-laadunseuranta.utils :refer [timestamp ipad?]]
            [harja-laadunseuranta.projektiot :as projektiot]))

(def +paikan-raportointivali+ 2000) ; ms

(defn- geolocation-api []
  (.-geolocation js/navigator))

(defn- geolokaatio-tuettu? []
  (not (nil? (geolocation-api))))

(defn- etaisyys
  "Laskee kahden koordinaatin (ETRS-TM35FIN) välisen etäisyyden metreinä"
  [a b]
  (let [xdist (- (:lon a) (:lon b))
        ydist (- (:lat a) (:lat b))]
    (Math/sqrt (+ (* xdist xdist) (* ydist ydist)))))

(defn- konvertoi-latlon
  "Muuntaa geolocation-apin antaman objektin cljs-otukseksi ja tekee
  WGS84 -> ETRS-TM35FIN -muunnoksen"
  [position]
  (let [coords (.-coords position)
        wgs84-lat (.-latitude coords)
        wgs84-lon (.-longitude coords)
        latlon (projektiot/wgs84->etrsfin [wgs84-lon wgs84-lat])]
    {:lat (aget latlon 1)
     :lon (aget latlon 0)
     :heading (or (.-heading coords) 0)
     :accuracy (.-accuracy coords)
     :speed (or (.-speed coords) 0)}))

(def paikannusoptiot #js {:enableHighAccuracy true
                          :maximumAge 1000
                          :timeout +paikan-raportointivali+
                          })

(defn- ms->sec
  "Muuntaa millisekunnit sekunneiksi"
  [s]
  (/ s 1000))

(defn paivita-sijainti [{:keys [nykyinen]} sijainti ts]
  (let [uusi-sijainti (assoc sijainti :timestamp ts)
        uusi-nykyinen (if (ipad?)
                        uusi-sijainti
                        (kalman/kalman nykyinen uusi-sijainti
                                       (ms->sec (- ts (or (:timestamp nykyinen) ts)))))]
    {:edellinen nykyinen
     :nykyinen (assoc uusi-nykyinen
                      :speed (:speed uusi-sijainti)
                      :heading (:heading uusi-sijainti)
                      :timestamp ts)}))

(defn kaynnista-paikannus [sijainti-atomi]
  (when (geolokaatio-tuettu?)
    (.log js/console "Paikannus käynnistetään")
    (js/setInterval (fn []
                      (.getCurrentPosition (geolocation-api)
                                           #(swap! sijainti-atomi (fn [entinen]
                                                                    (paivita-sijainti entinen (konvertoi-latlon %) (timestamp))))
                                           #(swap! sijainti-atomi identity)
                                           paikannusoptiot))
                    +paikan-raportointivali+)))

(defn aseta-testisijainti
  "HUOM: testikäyttöön. Asettaa nykyisen sijainnin koordinaatit. Oikean geolocation pollerin tulisi
  olla pois päältä kun tätä käytetään."
  [sijainti-atomi [x y]]
  (swap! sijainti-atomi
         (fn [{:keys [nykyinen]}]
           {:edellinen nykyinen
            :nykyinen {:lat y
                       :lon x
                       :heading 0
                       :accuracy 20
                       :speed 40
                       :timestamp (timestamp)}})))

(defn lopeta-paikannus [n]
  (when n
    (js/clearInterval n)))
