(ns harja-laadunseuranta.tiedot.paikannus
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.kalman :as kalman]
            [harja-laadunseuranta.utils :as utils]
            [harja.math :as math]
            [harja.geo :as geo]
            [harja-laadunseuranta.utils :refer [timestamp ipad?]]
            [harja-laadunseuranta.tiedot.projektiot :as projektiot]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]))

(def +max-maara-alustuksen-paikannnusyrityksia+ 10)

(defn- geolocation-api []
  (.-geolocation js/navigator))

(defn geolokaatio-tuettu? []
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
        latlon (geo/wgs84->etrsfin [wgs84-lon wgs84-lat])]
    {:lat (aget latlon 1)
     :lon (aget latlon 0)
     :heading (or (.-heading coords) 0)
     :accuracy (.-accuracy coords)
     :speed (or (.-speed coords) 0)}))

(def paikannusoptiot #js {:enableHighAccuracy true
                          :maximumAge 0
                          :timeout js/Infinity})

(defn paivita-sijainti [{:keys [nykyinen]} sijainti ts]
  (let [uusi-sijainti (assoc sijainti :timestamp ts)
        uusi-nykyinen (if (ipad?)
                        uusi-sijainti ;; iPadissa on ilmeisesti riittävä suodatus itsessään
                        (kalman/kalman nykyinen uusi-sijainti
                                       (math/ms->sec (- ts (or (:timestamp nykyinen) ts)))))]
    {:edellinen nykyinen
     :nykyinen (assoc uusi-nykyinen
                 :speed (:speed uusi-sijainti) ; m/s
                 :heading (:heading uusi-sijainti)
                 :accuracy (:accuracy uusi-sijainti) ; säde metreinä
                 :timestamp ts)}))

(defn kaynnista-paikannus
  [{:keys [sijainti-atom ensimmainen-sijainti-saatu-atom ensimmainen-sijainti-virhekoodi-atom
           ensimmainen-sijainti-yritys-atom]}]
  (when (geolokaatio-tuettu?)
    (.log js/console "Paikannus käynnistetään")
    (let [sijainti-saatu (fn [sijainti]
                           (let [tarkkuus (-> sijainti .-coords .-accuracy)
                                 riittavan-tarkka? (reitintallennus/nykyinen-sijainti-riittavan-tarkka?
                                                     tarkkuus
                                                     asetukset/+suurin-sallittu-tarkkuus+)]

                             (when (and ensimmainen-sijainti-saatu-atom
                                        (not @ensimmainen-sijainti-saatu-atom))
                               (reset! ensimmainen-sijainti-saatu-atom true))

                             (if riittavan-tarkka?
                               (swap! sijainti-atom (fn [entinen]
                                                      (paivita-sijainti entinen
                                                                        (konvertoi-latlon sijainti)
                                                                        (timestamp))))
                               (ilmoitukset/ilmoita (str "Paikannus epäonnistui: sijainti epätarkka (" tarkkuus "m)")
                                                    s/ilmoitus {:tyyppi :virhe}))))
          sijainti-epaonnistui (fn [virhe]
                                 (ilmoitukset/ilmoita (str "Paikannus epäonnistui: " (.-message virhe)) s/ilmoitus {:tyyppi :virhe})
                                 (when (and ensimmainen-sijainti-saatu-atom
                                            ensimmainen-sijainti-yritys-atom
                                            (not @ensimmainen-sijainti-saatu-atom))
                                   (swap! ensimmainen-sijainti-yritys-atom inc))
                                 (when (and ensimmainen-sijainti-saatu-atom
                                            ensimmainen-sijainti-virhekoodi-atom
                                            (>= @ensimmainen-sijainti-yritys-atom +max-maara-alustuksen-paikannnusyrityksia+)
                                            (not @ensimmainen-sijainti-saatu-atom))
                                   (.log js/console "Paikannus epäonnistui: " (.-message virhe))
                                   (reset! ensimmainen-sijainti-virhekoodi-atom (.-code virhe))
                                   (reset! ensimmainen-sijainti-saatu-atom false))
                                 (swap! sijainti-atom identity))]
      (.watchPosition (geolocation-api)
                      sijainti-saatu
                      sijainti-epaonnistui
                      paikannusoptiot))))

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
                       :speed (+ 25 (rand-int 5))
                       :timestamp (timestamp)}})))

(defn lopeta-paikannus [id]
  (when id
    (js/clearInterval id)
    (.log js/console "Paikannut lopetettu!")))
