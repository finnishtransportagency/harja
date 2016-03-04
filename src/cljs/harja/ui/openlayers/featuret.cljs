(ns harja.ui.openlayers.featuret
  "OpenLayers featureiden luonti Clojure data kuvauksien perusteella"
  (:require [ol.Feature]

            [ol.geom.Polygon]
            [ol.geom.Point]
            [ol.geom.Circle]
            [ol.geom.LineString]
            [ol.geom.MultiLineString]

            [ol.style.Style]
            [ol.style.Fill]
            [ol.style.Stroke]
            [ol.style.Icon]

            [harja.loki :refer [log]]

            [harja.ui.kartta.apurit :as apurit]
            [harja.geo :as geo]
            [harja.tiedot.navigaatio :as nav]))

(def ^{:doc "Viivaan piirrettävien nuolten välimatka, jotta nuolia ei piirretä
turhaan liikaa"
       :const true}
  nuolten-valimatka 3000)

(def ^{:doc "Kartalle piirrettävien asioiden oletus-zindex. Urakat ja muut piirretään
pienemmällä zindexillä." :const true}
  oletus-zindex 4)



(defmulti luo-feature :type)

(defn aseta-tyylit [feature {:keys [fill color stroke marker zindex] :as geom}]
  (doto feature
    (.setStyle (ol.style.Style.
                #js {:fill (when fill
                             (ol.style.Fill. #js {:color (or color "red")}))
                     :stroke (ol.style.Stroke.
                              #js {:color (or (:color stroke) "black")
                                   :width (or (:width stroke) 1)})
                     ;; Default zindex asetetaan harja.views.kartta:ssa.
                     ;; Default arvo on 4 - täällä 0 ihan vaan fallbackina.
                     ;; Näin myös pitäisi huomata jos tämä ei toimikkaan.
                     :zIndex (or zindex 0)}))))

(defn- tee-nuoli
  [kasvava-zindex {:keys [img scale zindex anchor rotation]} [piste rotaatio]]
  (ol.style.Style.
    #js {:geometry piste
         :zIndex   (or zindex (swap! kasvava-zindex inc))
         :image    (ol.style.Icon.
                     #js {:src            (str img)
                          :scale          (or scale 1)
                          :rotation       (or rotation rotaatio) ;; Rotaatio on laskettu, rotation annettu.
                          :anchor         (or (clj->js anchor) #js [0.5 0.5])
                          :rotateWithView false})}))

;; Käytetään sisäisesti :viiva featurea rakentaessa
(defn- tee-merkki
  [kasvava-zindex tiedot [piste _]]
  (tee-nuoli kasvava-zindex (merge {:anchor [0.5 1]} tiedot) [piste 0]))

;; Käytetään sisäisesti :viiva featurea rakentaessa
(defn- tee-ikonille-tyyli
  [zindex laske-taitokset-fn {:keys [tyyppi paikka img] :as ikoni}]
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
                              nuolten-valimatka (butlast (laske-taitokset-fn)))))
          pisteet-ja-rotaatiot (mapcat palauta-paikat (if (coll? paikka) paikka [paikka]))]
      (condp = tyyppi
        :nuoli (map #(tee-nuoli zindex ikoni %)
                    pisteet-ja-rotaatiot)
        :merkki (map #(tee-merkki zindex ikoni %) pisteet-ja-rotaatiot)))))

;; Käytetään sisäisesti :viiva featurea rakentaessa
(defn- tee-viivalle-tyyli
  [kasvava-zindex {:keys [color width zindex dash cap join miter]}]
  (ol.style.Style. #js {:stroke (ol.style.Stroke. #js {:color      (or color "black")
                                                       :width      (or width 2)
                                                       :lineDash   (or (clj->js dash) nil)
                                                       :lineCap    (or cap "round")
                                                       :lineJoin   (or join "round")
                                                       :miterLimit (or miter 10)})
                        :zindex (or zindex (swap! kasvava-zindex inc))}))

(defmethod luo-feature :viiva
  [{:keys [viivat points ikonit]}]
  (let [feature (ol.Feature. #js {:geometry (ol.geom.LineString. (clj->js points))})
        kasvava-zindex (atom oletus-zindex)
        taitokset (atom [])
        laske-taitokset (fn []
                          (if-not (empty? @taitokset)
                            @taitokset
                            (reset! taitokset
                                    (apurit/pisteiden-taitokset points))))
        tee-ikoni (partial tee-ikonille-tyyli kasvava-zindex laske-taitokset)
        tee-viiva (partial tee-viivalle-tyyli kasvava-zindex)
        tyylit (apply concat (mapv tee-viiva viivat) (mapv tee-ikoni ikonit))]
    (doto feature (.setStyle (clj->js tyylit)))))

(defmethod luo-feature :merkki [{:keys [coordinates img scale zindex anchor]}]
  (doto (ol.Feature. #js {:geometry (ol.geom.Point. (clj->js coordinates))})
    (.setStyle (ol.style.Style.
                 #js {:image  (ol.style.Icon.
                                #js {:src    (str img)
                                     :anchor (or (clj->js anchor) #js [0.5 1])
                                     :scale  (or scale 1)})
                      :zIndex (or zindex oletus-zindex)}))))



(defmethod luo-feature :polygon [{:keys [coordinates] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.Polygon. (clj->js [coordinates]))}))

(defmethod luo-feature :icon [{:keys [coordinates img direction anchor]}]
  (doto (ol.Feature. #js {:geometry (ol.geom.Point. (clj->js coordinates))})
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

(defmethod luo-feature :point [{:keys [coordinates radius] :as point}]
  #_(ol.Feature. #js {:geometry (ol.geom.Point. (clj->js coordinates))})
  (luo-feature (assoc point
                 :type :circle
                 :radius (or radius 10))))

(defmethod luo-feature :circle [{:keys [coordinates radius]}]
  (ol.Feature. #js {:geometry (ol.geom.Circle. (clj->js coordinates) radius)}))


(defmethod luo-feature :multipolygon [{:keys [polygons] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.Polygon. (clj->js (mapv :coordinates polygons)))}))

(defmethod luo-feature :multiline [{:keys [lines] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.MultiLineString. (clj->js (mapv :points lines)))}))


(defmethod luo-feature :line [{:keys [points] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.LineString. (clj->js points))}))
