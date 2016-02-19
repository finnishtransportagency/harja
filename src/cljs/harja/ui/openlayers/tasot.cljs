(ns harja.ui.openlayers.tasot
  "Määrittelee karttatason kaltaisen protokollan"
  (:require [harja.ui.openlayers.featuret :as featuret]
            [harja.loki :refer [log]]))

(defprotocol Taso
  (aseta-z-index [this z-index]
                 "Palauttaa uuden version tasosta, jossa zindex on asetettu")
  (extent [this] "Palauttaa tason geometrioiden extentin [minx miny maxx maxy]")
  (selitteet [this] "Palauttaa tällä tasolla olevien asioiden selitteet")
  (paivita-ol-taso
   [this ol3 ol-layer aiempi-paivitystieto]
   "Päivitä ol-layer tai luo uusi layer. Tulee palauttaa vektori, jossa on
ol3 Layer objekti ja tälle tasolle spesifinen päivitystieto. Palautettu
päivitystieto annettaan seuraavalla kerralla aiempi-paivitystieto
parametrina takaisin."))

(defn- luo-feature [geom]
  (try
    (featuret/luo-feature geom)
    (catch js/Error e
      (log (pr-str e))
      (log (pr-str "Problem in luo-feature, geom: " geom))
      nil)))

(defn- create-geometry-layer
  "Create a new ol3 Vector layer with a vector source."
  []
  (ol.layer.Vector. #js {:source          (ol.source.Vector.)
                         :rendererOptions {:zIndexing true
                                           :yOrdering true}}))

(defn aseta-feature-geometria! [feature geometria]
  (.set feature "harja-geometria" geometria))

(def ^{:doc "Tyypit, joille pitää kutsua aseta-tyylit" :private true}
  tyyppi-tarvitsee-tyylit
  #{:polygon :point :circle :multipolygon :multiline :line})

(defn update-ol3-layer-geometries
  "Given a vector of ol3 layer and map of current geometries and a
  sequence of new geometries, updates (creates/removes) the geometries
  in the layer to match the new items. Returns a new vector with the updates
  ol3 layer and map of geometries.
  If incoming layer & map vector is nil, a new ol3 layer will be created."
  [ol3 geometry-layer geometries-map items]
  (let [create? (nil? geometry-layer)
        geometry-layer (if create?
                         (doto (create-geometry-layer)
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
      (log "POISTA FEATURE " feature " ,jonka avain: " (pr-str avain))
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
  (selitteet [this]
    (-> this meta :selitteet))
  (paivita-ol-taso [items ol3 ol-layer aiempi-paivitystieto]
    (update-ol3-layer-geometries ol3 ol-layer aiempi-paivitystieto items)))
