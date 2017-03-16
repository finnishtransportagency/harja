(ns harja.ui.openlayers.geometriataso
  "Geometriataso, joka muodostaa tason vektorista mäppejä."
  (:require [harja.ui.openlayers.featuret :as featuret]
            [harja.ui.openlayers.taso :as taso :refer [Taso]]
            [harja.loki :refer [log]]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- luo-feature [geom]
  (try
    (featuret/luo-feature geom)
    (catch js/Error e
      (log (pr-str e))
      (log (pr-str "luo-feature palautti virheen, geom: " geom))
      nil)))

(defn- create-geometry-layer
  "Create a new ol3 Vector layer with a vector source."
  [opacity]
  (ol.layer.Vector. #js {:source          (ol.source.Vector.)
                         :rendererOptions {:zIndexing true
                                           :yOrdering true}
                         :opacity opacity}))

(defn aseta-feature-geometria! [feature geometria]
  (.set feature "harja-geometria" geometria))

(def ^{:doc "Tyypit, joille pitää kutsua aseta-tyylit" :private true}
  tyyppi-tarvitsee-tyylit
  #{:polygon :point :circle :multipolygon :multiline :line
    :geometry-collection})

(defn update-ol3-layer-geometries
  "Given a vector of ol3 layer and map of current geometries and a
  sequence of new geometries, updates (creates/removes) the geometries
  in the layer to match the new items. Returns a new vector with the updates
  ol3 layer and map of geometries.
  If incoming layer & map vector is nil, a new ol3 layer will be created."
  [ol3 geometry-layer geometries-map items]
  (let [create? (nil? geometry-layer)
        geometry-layer (if create?
                         (doto (create-geometry-layer
                                (taso/opacity items))
                           (.setZIndex (or (:zindex (meta items)) 0)))
                         geometry-layer)
        geometries-map (if create? {} geometries-map)
        geometries-set (into #{} items)
        features (.getSource geometry-layer)]

    (when create?
      (.addLayer ol3 geometry-layer))

    ;; Remove all ol3 feature objects that are no longer in the new geometries
    (doseq [[avain feature] (seq geometries-map)
            :when (and feature (not (geometries-set avain)))]
      (.removeFeature features feature))

    ;; Create new features for new geometries and update the geometries map
    (loop [new-geometries-map {}
           [item & items] items]
      (if-not item
        ;; When all items are processed, return layer and new geometries map
        [geometry-layer new-geometries-map]

        (let [alue (:alue item)]
          (recur
           (assoc new-geometries-map item
                  (or (geometries-map item)
                      (when-let [new-shape (luo-feature alue)]
                        (aseta-feature-geometria! new-shape item)
                        (.addFeature features new-shape)

                        ;; Aseta geneerinen tyyli tyypeille,
                        ;; joiden luo-feature ei sitä tee
                        (when (tyyppi-tarvitsee-tyylit (:type alue))
                          (featuret/aseta-tyylit new-shape alue))

                        new-shape)))
           items))))))

;; Laajenna vektorit olemaan tasoja
(extend-protocol Taso
  PersistentVector
  (aseta-z-index [this z-index]
    (with-meta this
      (merge (meta this)
             {:zindex z-index})))
  (extent [this]
    (-> this meta :extent))
  (opacity [this]
    (or (-> this meta :opacity) 1))
  (selitteet [this]
    (-> this meta :selitteet))
  (aktiivinen? [this]
    (some? (taso/extent this)))
  (paivita [items ol3 ol-layer aiempi-paivitystieto]
    (update-ol3-layer-geometries ol3 ol-layer aiempi-paivitystieto items))
  (hae-asiat-pisteessa [this koordinaatti extent]
    (let [ch (async/chan)]
      (if-let [hae-asiat (-> this meta :hae-asiat)]
        (go
          (doseq [asia (hae-asiat koordinaatti)]
            (>! ch asia))
          (async/close! ch))
        (async/close! ch))
      ch)))
