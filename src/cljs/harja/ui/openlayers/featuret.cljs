(ns harja.ui.openlayers.featuret
  "OpenLayers featureiden luonti Clojure data kuvauksien perusteella"
  (:require [ol.Feature]

            [ol.geom.Polygon]
            [ol.geom.MultiPolygon]
            [ol.geom.Point]
            [ol.geom.Circle]
            [ol.geom.LineString]
            [ol.geom.MultiLineString]
            [ol.geom.GeometryCollection]

            [ol.style.Style]
            [ol.style.Fill]
            [ol.style.Stroke]
            [ol.style.Icon]
            [ol.style.Circle]

            [harja.loki :refer [log]]

            [harja.ui.kartta.apurit :as apurit]
            [harja.geo :as geo]
            [harja.tiedot.navigaatio :as nav]))

(def ^{:doc "Viivaan piirrettävien nuolten välimatka, jotta nuolia ei piirretä
turhaan liikaa. Kasvata lukua = Vähemmän nuolia."
       :const true
       :private true}
maksimi-valimatka 200)
(def ^{:doc "Viivaan piirrettävien nuolten minimietäisyys, jotta taitoksissa nuolia ei piirretä l
iian lähekkäin. Kasvata lukua = vähemmän nuolia, käännöksiin reagoidaan vähemmän."
       :const true
       :private true}
minimi-valimatka 50)

(def ^{:doc "Kartalle piirrettävien asioiden oletus-zindex. Urakat ja muut piirretään
pienemmällä zindexillä." :const true}
  oletus-zindex 4)



(defmulti luo-feature :type)
(defmulti luo-geometria :type)

(defn- circle-style [{:keys [fill stroke radius] :as circle}]
  (ol.style.Circle. #js {:fill (ol.style.Fill. #js {:color fill})
                         :stroke (ol.style.Stroke. #js {:color stroke :width 1})
                         :radius (:radius circle)
                         }))

(defn aseta-tyylit [feature {:keys [fill color stroke marker zindex circle] :as geom}]
  (doto feature
    (.setStyle (ol.style.Style.
                #js {:image (some-> circle circle-style)
                     :fill (when fill
                             (ol.style.Fill. #js {:color (or color "red")}))
                     :stroke (ol.style.Stroke.
                              #js {:color (or (:color stroke) "black")
                                   :width (or (:width stroke) 1)})
                     ;; Default zindex asetetaan harja.views.kartta:ssa.
                     ;; Default arvo on 4 - täällä 0 ihan vaan fallbackina.
                     ;; Näin myös pitäisi huomata jos tämä ei toimikkaan.
                     :zIndex (or zindex 0)
                     }))))

(defn- tee-nuoli
  [kasvava-zindex {:keys [img scale zindex anchor rotation]} [piste rotaatio] reso]
  (ol.style.Style.
    #js {:geometry piste
         :zIndex (or zindex (swap! kasvava-zindex inc))
         :image (ol.style.Icon.
                  #js {:src (str img)
                       :scale (apurit/ikonin-skaala-resoluutiolle reso (or scale 1))
                       :rotation (or rotation rotaatio) ;; Rotaatio on laskettu, rotation annettu.
                       :anchor (or (clj->js anchor) #js [0.5 0.5])
                       :rotateWithView false})}))

;; Käytetään sisäisesti :viiva featurea rakentaessa
(defn- tee-merkki
  [kasvava-zindex tiedot [piste _] reso]
  (tee-nuoli kasvava-zindex (merge {:anchor [0.5 1]} tiedot) [piste 0] reso))

;; Käytetään sisäisesti :viiva featurea rakentaessa
(defn- tee-ikonille-tyyli
  [zindex laske-taitokset-fn {:keys [tyyppi paikka img] :as ikoni} reso]
  ;; Kokonaisuus koodattiin alunperin sillä oletuksella, että :viivalle piirrettäisiin aina jokin ikoni.
  ;; Oletuksena pieni merkki reitin loppuun. Tuli kuitenkin todettua, että esim tarkastukset joissa ei ilmennyt
  ;; mitään halutaan todnäk vaan piirtää hyvin haalealla harmaalla tms. Tällaisissa tapauksissa :img arvoa
  ;; ei ole määritelty, eikä siis haluta piirtää mitään.
  (when img
    (assert (#{:nuoli :merkki} tyyppi) "Merkin tyypin pitää olla joko :nuoli tai :merkki")
    (let [palauta-paikat (fn [paikka]
                           (assert (#{:alku :loppu :taitokset} paikka)
                                   "Merkin paikan pitää olla :alku, :loppu, :taitokset")
                           (condp = paikka
                             :alku
                             [[(-> (laske-taitokset-fn) first :sijainti first clj->js ol.geom.Point.)
                               (-> (laske-taitokset-fn) first :rotaatio)]]
                             :loppu
                             [[(-> (laske-taitokset-fn) last :sijainti second clj->js ol.geom.Point.)
                               (-> (laske-taitokset-fn) last :rotaatio)]]
                             :taitokset
                             (apurit/taitokset-valimatkoin
                               (* minimi-valimatka reso) (* maksimi-valimatka reso) (butlast (laske-taitokset-fn)))))
          pisteet-ja-rotaatiot (mapcat palauta-paikat (if (coll? paikka) paikka [paikka]))]
      (condp = tyyppi
        :nuoli (map #(tee-nuoli zindex ikoni % reso)
                    pisteet-ja-rotaatiot)
        :merkki (map #(tee-merkki zindex ikoni % reso) pisteet-ja-rotaatiot)))))

;; Käytetään sisäisesti :viiva featurea rakentaessa
(defn- tee-viivalle-tyyli
  [kasvava-zindex {:keys [color width zindex dash cap join miter]} reso]
  (ol.style.Style. #js {:stroke (ol.style.Stroke. #js {:color (or color "black")
                                                       :width (or width 2)
                                                       :lineDash (or (clj->js dash) nil)
                                                       :lineCap (or cap "round")
                                                       :lineJoin (or join "round")
                                                       :miterLimit (or miter 10)})
                        :zindex (or zindex (swap! kasvava-zindex inc))}))

(defmethod luo-geometria :viiva [{points :points}]
  (ol.geom.LineString. (clj->js points)))

(defn luo-viiva
  [{:keys [viivat points ikonit] :as viiva}]
  (let [feature (ol.Feature. #js {:geometry (luo-geometria viiva)})
        kasvava-zindex (atom oletus-zindex)
        taitokset (atom [])
        laske-taitokset (fn []
                          (if-not (empty? @taitokset)
                            @taitokset
                            (reset! taitokset
                                    (apurit/pisteiden-taitokset points false))))
        tee-ikoni #(partial tee-ikonille-tyyli kasvava-zindex laske-taitokset %)
        tee-viiva #(partial tee-viivalle-tyyli kasvava-zindex %)
        tyylit (concat (mapv tee-viiva viivat) (mapv tee-ikoni ikonit))]
    (doto feature (.setStyle (fn [reso]
                               (clj->js (flatten (keep (fn [f] (f reso)) tyylit))))))))

(defmethod luo-feature :viiva
  [viiva]
  (luo-viiva viiva))

(defmethod luo-geometria :moniviiva
  [{lines :lines}]
  (ol.geom.MultiLineString.
   (clj->js (mapv :points lines))))

(defmethod luo-feature :moniviiva
  [{:keys [lines] :as moniviiva}]
  (luo-viiva (assoc moniviiva :points (mapcat :points lines))))

(defmethod luo-geometria :merkki
  [{c :coordinates}]
  (ol.geom.Point. (clj->js c)))

(defmethod luo-feature :merkki [{:keys [coordinates img scale zindex anchor] :as merkki}]
  (doto (ol.Feature. #js {:geometry (luo-geometria merkki)})
    (.setStyle (ol.style.Style.
                 #js {:image  (ol.style.Icon.
                                #js {:src    (str img)
                                     :anchor (or (clj->js anchor) #js [0.5 1])
                                     :scale  (or scale 1)})
                      :zIndex (or zindex oletus-zindex)}))))


(defmethod luo-geometria :polygon [{c :coordinates}]
  (ol.geom.Polygon. (clj->js [c])))

(defmethod luo-geometria :icon [{c :coordinates}]
  (ol.geom.Point. (clj->js c)))

(defmethod luo-feature :icon [{:keys [coordinates img direction anchor] :as icon}]
  (doto (ol.Feature. #js {:geometry (luo-geometria icon)})
    (.setStyle (ol.style.Style.
                 #js {:image  (ol.style.Icon.
                                #js {:src          img
                                     :anchor       (if anchor
                                                     (clj->js anchor)
                                                     #js [0.5 1])
                                     :opacity      1
                                     :rotation     (or direction 0)
                                     :anchorXUnits "fraction"
                                     :anchorYUnits "fraction"})
                      :zIndex oletus-zindex}))))

(defmethod luo-geometria :point [{c :coordinates}]
  (ol.geom.Point. (clj->js c)))

(defmethod luo-feature :point [{:keys [coordinates radius] :as point}]
  (aseta-tyylit
   (luo-feature (assoc point
                       :type :circle
                       :radius (or radius 10)))
   point))

(defmethod luo-geometria :circle [{:keys [coordinates radius]}]
  (ol.geom.Circle. (clj->js coordinates) radius))

(defmethod luo-geometria :multipolygon [{polygons :polygons}]
  (let [multi (ol.geom.MultiPolygon.)]
    (doseq [polygon polygons]
      (.appendPolygon multi (ol.geom.Polygon. (clj->js [(:coordinates polygon)]))))
    multi))

(defmethod luo-geometria :multiline [{lines :lines}]
  (ol.geom.MultiLineString. (clj->js (mapv :points lines))))

(defmethod luo-geometria :line [{points :points}]
  (ol.geom.LineString. (clj->js points)))

(defmethod luo-geometria :geometry-collection [{gs :geometries}]
  (ol.geom.GeometryCollection. (clj->js (mapv luo-geometria gs))))

(defmethod luo-feature :geometry-collection [gc]
  (aseta-tyylit
   (ol.Feature. #js {:geometry (luo-geometria gc)})
   gc))

(defmethod luo-feature :default [this]
  (ol.Feature. #js {:geometry (luo-geometria this)}))
