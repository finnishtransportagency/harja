(ns harja.ui.openlayers.kuvataso
  "Taso, joka hakee kuvan Harja palvelimelta"
  (:require [kuvataso.Lahde]
            [ol.Image]
            [ol.layer.Image]
            [ol.source.Image]
            [ol.source.ImageStatic]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :refer [karttakuva-url]]
            [harja.ui.openlayers.taso :refer [Taso]]))

(defn- ol-kuva [extent resolution url]
  (ol.Image. extent resolution 1 nil url "use-credentials"
             ol.source.Image/defaultImageLoadFunction))

(defn hae-fn [parametrit]
  (let [kuva (atom nil)]
    (fn [extent resolution pixel-ratio projection]
      (second
       (swap! kuva
              (fn [[url image]]
                (let [[x1 y1 x2 y2] extent
                      uusi-url (apply karttakuva-url
                                      (concat  ["x1" x1 "y1" y1 "x2" x2 "y2" y2
                                                "r" resolution "pr" pixel-ratio]
                                               parametrit))]
                  (if (= uusi-url url)
                    [url image]
                    (do (log "KUVA URL: " url " => " uusi-url)
                        [uusi-url
                         (ol-kuva extent resolution uusi-url)])))))))))

(defrecord Kuvataso [projection extent z-index  selitteet parametrit]
  Taso
  (aseta-z-index [this z-index]
    (assoc this :z-index z-index))
  (extent [this]
    extent)
  (selitteet [this]
    selitteet)
  (paivita [this ol3 ol-layer aiempi-paivitystieto]
    (let [sama? (= parametrit aiempi-paivitystieto)
          luo? (nil? ol-layer)
          source (if (and sama? (not luo?))
                   (.getSource ol-layer)
                   (kuvataso.Lahde. (hae-fn parametrit)
                                    #js {:projection projection
                                         :imageExtent extent}))

          ol-layer (or ol-layer
                       (ol.layer.Image.
                        #js {:source source
                             :wrapX true}))]

      (when luo?
        (.addLayer ol3 ol-layer))

      (when z-index
        (.setZIndex ol-layer z-index))

      (when (and (not luo?) (not sama?))
        ;; Jos ei luoda ja parametrit eivät ole samat
        ;; asetetaan uusi source ol layeriiin
        (.setSource ol-layer source))
      [ol-layer ::kuvataso])))




(defn luo-kuvataso [projection extent selitteet parametrit]
  (->Kuvataso projection extent 99 selitteet parametrit))
