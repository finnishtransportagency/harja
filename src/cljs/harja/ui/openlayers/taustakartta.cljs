(ns harja.ui.openlayers.taustakartta
  "Taustakarttatasojen muodostus. Luo karttakomponentille annetun määrittelyn
  perusteella sopivat OpenLayersin WMTS tasojen objektit."
  (:require [ol.source.WMTS]
            [ol.tilegrid.WMTS]
            [ol.layer.Tile]
            [ol.extent :as ol-extent]
            [harja.ui.openlayers.projektiot :as p]
            [taoensso.timbre :as log]))

(defn mml-tilegrid []
  (let [koko (/ (ol-extent/getWidth (.getExtent p/projektio)) 256)]
    (loop [resoluutiot []
           matrix-idt []
           i 0]
      (if (= i 16)
        (let [optiot (clj->js
                      {:origin (ol-extent/getTopLeft (.getExtent p/projektio))
                       :resolutions (clj->js resoluutiot)
                       :matrixIds   (clj->js matrix-idt)})]
          (ol.tilegrid.WMTS. optiot))
        (recur (conj resoluutiot (/ koko (Math/pow 2 i)))
               (conj matrix-idt i)
               (inc i))))))

(defn livi-tilegrid []
  (ol.tilegrid.WMTS.
   (clj->js {:origin (ol-extent/getTopLeft (.getExtent p/projektio-livi))
             :resolutions [8192 4096 2048 1024 512 256 128 64 32 16 8 4 2 1 0.5 0.25]
             :matrixIds (range 16)
             :tileSize 256})))

(defn- wmts-layer [projektio matrixset tilegrid-fn attribuutio url layer]
  (ol.layer.Tile.
   #js {:source
        (ol.source.WMTS. #js {:attributions [(ol.Attribution.
                                              #js {:html attribuutio})]
                              :url          url
                              :layer        layer
                              :matrixSet    matrixset
                              :format       "image/png"
                              :projection   projektio
                              :tileGrid     (tilegrid-fn)
                              :style        "default"
                              :wrapX        true})}))

(defmulti luo-taustakartta :type)

(defmethod luo-taustakartta :mml [{:keys [url layer]}]
  (log/info "Luodaan MML karttataso: " layer)
  (wmts-layer p/projektio "ETRS-TM35FIN" mml-tilegrid "MML" url layer))

(defmethod luo-taustakartta :livi [{:keys [url layer]}]
  (log/info "Luodaan livi karttataso: layer")
  (wmts-layer p/projektio-livi "EPSG:3067_PTP_JHS180" livi-tilegrid "Liikennevirasto"
              url layer))
