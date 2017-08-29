(ns harja.ui.openlayers.taustakartta
  "Taustakarttatasojen muodostus. Luo karttakomponentille annetun määrittelyn
  perusteella sopivat OpenLayersin WMTS tasojen objektit."
  (:require [ol.source.WMTS]
            [ol.tilegrid.WMTS]
            [ol.layer.Tile]
            [ol.source.ImageWMS]
            [ol.layer.Image]
            [ol.extent :as ol-extent]
            [harja.ui.openlayers.projektiot :as p]
            [taoensso.timbre :as log]))


(defn tilegrid []
  (ol.tilegrid.WMTS.
   (clj->js {:origin (ol-extent/getTopLeft (.getExtent p/projektio))
             :resolutions [8192 4096 2048 1024 512 256 128 64 32 16 8 4 2 1 0.5 0.25]
             :matrixIds (range 16)
             :tileSize 256})))

(defn- wmts-layer [matrixset attribuutio url layer visible?]
  (doto (ol.layer.Tile.
         #js {:source
              (ol.source.WMTS. #js {:attributions [(ol.Attribution.
                                                    #js {:html attribuutio})]
                                    :url          url
                                    :layer        layer
                                    :matrixSet    matrixset
                                    :format       "image/png"
                                    :projection   p/projektio
                                    :tileGrid     (tilegrid)
                                    :style        "default"
                                    :wrapX        true})})
    (.setVisible visible?)))

(defmulti luo-taustakartta :type)

(defmethod luo-taustakartta :mml [{:keys [url layer default]}]
  (log/info "Luodaan MML karttataso: " layer)
  (wmts-layer "ETRS-TM35FIN" "MML" url layer default))

(defmethod luo-taustakartta :livi [{:keys [url layer default]}]
  (log/info "Luodaan livi karttataso: layer")
  (wmts-layer "EPSG:3067_PTP_JHS180" "Liikennevirasto" url layer default))

(defmethod luo-taustakartta :wms [{:keys [url layer style default] :as params}]
  (log/info "Luodaan WMS karttataso: " params)
  (doto (ol.layer.Image.
         #js {:source (ol.source.ImageWMS.
                       #js {:url url
                            :params #js {:LAYERS layer :STYLE style :FORMAT "image/png"}})})
    (.setVisible default)))
