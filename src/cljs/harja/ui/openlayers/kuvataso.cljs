(ns harja.ui.openlayers.kuvataso
  "Taso, joka hakee kuvan Harja palvelimelta"
  (:require [ol.layer.Image]
            [ol.source.Image]
            [ol.Image]
            [ol.Attribution]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :refer [karttakuva-url]])
  (:require-macros [harja.makrot :refer [goog-extend]]))

(def viimeksi-haettu-kuva (atom nil))

(goog-extend
 Kuvalahde ol.source.Image
 ([options]
  (goog/base (js* "this") options)
  (log "[Kuvalahde] luotu.. optiot: " (pr-str options)))

 (getImage
  [extent resolution pixel-ratio projection]

  #_(log "[Kuvalahde] extent=" extent ", resolution=" resolution ", pixel-ratio=" pixel-ratio ", projection=" projection)
  (reset! viimeksi-haettu-kuva
         (ol.Image. extent resolution 2
                    (clj->js [(ol.Attribution. #js {:html "foo"})])
                    (let [[x1 y1 x2 y2] extent]
                      (karttakuva-url "x1" x1 "y1" y1 "x2" x2 "y2" y2
                                      "r" resolution "pr" pixel-ratio))
                    nil ol.source.Image/defaultImageLoadFunction))))

(defn luo-kuvataso [projection extent]
  (doto (ol.layer.Image. (clj->js {:source (Kuvalahde.
                                            #js {:projection projection
                                                 :imageExtent extent})
                                   :wrapX true}))
    (.setZIndex 99)))
