(ns harja.ui.openlayers.kuvataso
  "Taso, joka hakee kuvan Harja palvelimelta"
  (:require [kuvataso.Lahde]
            [ol.Image]
            [ol.layer.Image]
            [ol.source.Image]
            [ol.source.ImageStatic]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :refer [karttakuva-url]]))

(defn hae-fn []
  (let [kuva (atom nil)]
    (fn [extent resolution pixel-ratio projection]
      (second
       (swap! kuva
              (fn [[url image]]
                (let [[x1 y1 x2 y2] extent
                      uusi-url (karttakuva-url "x1" x1 "y1" y1 "x2" x2 "y2" y2
                                               "r" resolution "pr" pixel-ratio)]
                  (if (= uusi-url url)
                    [url image]
                    (do (log "UUSI KUVA URL: " uusi-url)
                        [uusi-url
                         (ol.Image. extent resolution 1 nil
                                    uusi-url ""
                                    ol.source.Image/defaultImageLoadFunction)])))))))))


(defn luo-kuvataso [projection extent]
  (doto (ol.layer.Image.
         (clj->js {:source (kuvataso.Lahde. (hae-fn)
                                            #js {:projection projection
                                                 :imageExtent extent})
                   :wrapX true}))
    (.setZIndex 99)))
