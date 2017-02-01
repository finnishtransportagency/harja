(ns harja-laadunseuranta.tiedot.paikannus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.kalman :as kalman]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.math :as math]
            [harja-laadunseuranta.utils :refer [timestamp ipad?]]
            [harja-laadunseuranta.tiedot.projektiot :as projektiot]
            [cljs-time.local :as l]))

(def +paikannuksen-oletustimeout+ 2000)
(def paikannuksen-timeout-ms (atom +paikannuksen-oletustimeout+))
(def +paikannuksen-timeoutin-kasvatusvali+ 1000)
(def paikannuksen-timeout-ms-max 5000)
(def paikannuksen-vali-ms 2000)

(def paikannus-id (atom nil))

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

(defn tee-paikannusoptiot []
  #js {:enableHighAccuracy true
       :maximumAge 1000
       :timeout @paikannuksen-timeout-ms})

(defn paivita-sijainti [{:keys [nykyinen]} sijainti ts]
  (let [uusi-sijainti (assoc sijainti :timestamp ts)
        uusi-nykyinen (if (ipad?)
                        uusi-sijainti ;; iPadissa on ilmeisesti riittävä suodatus itsessään
                        (kalman/kalman nykyinen uusi-sijainti
                                       (math/ms->sec (- ts (or (:timestamp nykyinen) ts)))))]
    {:edellinen nykyinen
     :nykyinen (assoc uusi-nykyinen
                 :speed (:speed uusi-sijainti)
                 :heading (:heading uusi-sijainti)
                 :accuracy (:accuracy uusi-sijainti)
                 :timestamp ts)}))

(defn- yrita-kasvattaa-paikannuksen-raportointivalia []
  (let [kasvatettu-raportointivali (+ @paikannuksen-timeout-ms +paikannuksen-timeoutin-kasvatusvali+)]
    (when (<= kasvatettu-raportointivali paikannuksen-timeout-ms-max)
      (reset! paikannuksen-timeout-ms kasvatettu-raportointivali))))

(defn- palauta-oletusraportointivali []
  (reset! paikannuksen-timeout-ms +paikannuksen-oletustimeout+))

(defn- paikanna-laite-jatkuvasti
  "Käynnistää paikannuksen tietyllä id:llä. Lopettaa jos paikannus-id vaihtuu."
  [{:keys [id sijainti-atom ensimmainen-sijainti-saatu-atom
           ensimmainen-sijainti-virhekoodi-atom]}]
  (when (= @paikannus-id id)
    (let [paikanna-uudelleen (fn [] (js/setTimeout #(paikanna-laite-jatkuvasti
                                                      {:id id
                                                       :sijainti-atom sijainti-atom
                                                       :ensimmainen-sijainti-saatu-atom ensimmainen-sijainti-saatu-atom
                                                       :ensimmainen-sijainti-virhekoodi-atom ensimmainen-sijainti-virhekoodi-atom})
                                                   paikannuksen-vali-ms))
          sijainti-saatu (fn [sijainti]
                           (when (= @paikannus-id id)
                             (palauta-oletusraportointivali)
                             (when (and ensimmainen-sijainti-saatu-atom
                                        (nil? @ensimmainen-sijainti-saatu-atom))
                               (reset! ensimmainen-sijainti-saatu-atom true))
                             (swap! sijainti-atom (fn [entinen]
                                                    (paivita-sijainti entinen (konvertoi-latlon sijainti) (timestamp))))
                             (paikanna-uudelleen)))
          sijainti-epaonnistui (fn [virhe]
                                 (when (= @paikannus-id id)
                                   (yrita-kasvattaa-paikannuksen-raportointivalia)
                                   (.log js/console "Paikannus epäonnistui (virhe: " (.-message virhe) " ), uudet optiot: " (tee-paikannusoptiot))
                                   (when (and ensimmainen-sijainti-saatu-atom ensimmainen-sijainti-virhekoodi-atom
                                              (nil? @ensimmainen-sijainti-saatu-atom)
                                              (>= @paikannuksen-timeout-ms paikannuksen-timeout-ms-max))
                                     (reset! ensimmainen-sijainti-virhekoodi-atom (.-code virhe))
                                     (reset! ensimmainen-sijainti-saatu-atom false))
                                   (swap! sijainti-atom identity)
                                   (paikanna-uudelleen)))]
      (.getCurrentPosition (geolocation-api)
                           sijainti-saatu
                           sijainti-epaonnistui
                           (tee-paikannusoptiot)))))

(defn kaynnista-paikannus
  ([sijainti-atom] (kaynnista-paikannus sijainti-atom nil nil))
  ([sijainti-atom ensimmainen-sijainti-saatu-atom ensimmainen-sijainti-virhekoodi-atom]
   (let [tama-paikannus-id (hash (l/local-now))]
     (when (geolokaatio-tuettu?)
       (.log js/console "Paikannus käynnistetään")
       (reset! paikannus-id tama-paikannus-id)
       (paikanna-laite-jatkuvasti
         {:id tama-paikannus-id
          :sijainti-atom sijainti-atom
          :ensimmainen-sijainti-saatu-atom ensimmainen-sijainti-saatu-atom
          :ensimmainen-sijainti-virhekoodi-atom ensimmainen-sijainti-virhekoodi-atom})))))

(defn aseta-testisijainti
  "HUOM: testikäyttöön. Asettaa nykyisen sijainnin koordinaatit. Oikean geolocation pollerin tulisi
  olla pois päältä kun tätä käytetään."
  [sijainti-atomi [x y] tarkkuus]
  (swap! sijainti-atomi
         (fn [{:keys [nykyinen]}]
           {:edellinen nykyinen
            :nykyinen {:lat y
                       :lon x
                       :heading 0
                       :accuracy tarkkuus
                       :speed 40
                       :timestamp (timestamp)}})))

(defn lopeta-paikannus []
  (reset! paikannus-id nil)
  (.log js/console "Paikannut lopetettu!"))
