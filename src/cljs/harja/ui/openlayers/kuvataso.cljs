(ns harja.ui.openlayers.kuvataso
  "Taso, joka hakee kuvan Harja palvelimelta"
  (:require [kuvataso.Lahde]
            [ol.layer.Tile]
            [ol.source.TileImage]
            [ol.extent :as ol-extent]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :refer [karttakuva-url]]
            [harja.ui.openlayers.edistymispalkki :as palkki]
            [harja.ui.openlayers.taso :refer [Taso]]
            [cljs-time.core :as t]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-url [source parametrit coord pixel-ratio projection]
  (let [tile-grid (.getTileGridForProjection source projection)
        extent (.getTileCoordExtent tile-grid coord
                               (ol-extent/createEmpty))
        [x1 y1 x2 y2] extent]
    (apply karttakuva-url
           (concat  ["x1" x1 "y1" y1 "x2" x2 "y2" y2
                     "r" (.getResolution tile-grid (aget coord 0))
                     "pr" pixel-ratio]
                    parametrit))))

(defrecord Kuvataso [projection extent z-index selitteet parametrit]
  Taso
  (aseta-z-index [this z-index]
    (assoc this :z-index z-index))
  (extent [this]
    extent)
  (opacity [this] 1)
  (selitteet [this]
    selitteet)
  (paivita [this ol3 ol-layer aiempi-paivitystieto]
    (let [sama? (= parametrit aiempi-paivitystieto)
          luo? (nil? ol-layer)
          source (if (and sama? (not luo?))
                   (.getSource ol-layer)
                   (doto (ol.source.TileImage. #js {:projection projection})
                     (.on "tileloadstart" palkki/kuvataso-aloita-lataus!)
                     (.on "tileloadend" palkki/kuvataso-lataus-valmis!)
                     (.on "tileloaderror" palkki/kuvataso-lataus-valmis!)))

          ol-layer (or ol-layer
                       (ol.layer.Tile.
                        #js {:source source
                             :wrapX true}))]

      (.setTileUrlFunction source (partial hae-url source parametrit))
      (when luo?
        (.addLayer ol3 ol-layer))

      (when z-index
        (.setZIndex ol-layer z-index))

      (when (and (not luo?) (not sama?))
        ;; Jos ei luoda ja parametrit eivät ole samat
        ;; asetetaan uusi source ol layeriiin
        (.setSource ol-layer source))
      [ol-layer ::kuvataso]))

  (hae-asiat-pisteessa [this koordinaatti extent]
    (let [ch (async/chan)]
      (go
        (let [asiat (<! (k/post! :karttakuva-klikkaus
                                 {:parametrit (into {}
                                                    (map vec)
                                                    (partition 2 parametrit))
                                  :koordinaatti koordinaatti
                                  :extent extent}))]
          (doseq [asia asiat]
            (async/>! ch asia))
          (async/close! ch)))
      ch)))


(defn luo-kuvataso [projection extent selitteet parametrit]
  (->Kuvataso projection extent 99 selitteet parametrit))

(defn sama? [kt1 kt2]
  (and (instance? Kuvataso kt1)
       (instance? Kuvataso kt2)
       (= (:selitteet kt1) (:selitteet kt2))
       (= (:parametrit kt1) (:parametrit kt2))))
