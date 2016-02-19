(ns harja.ui.openlayers.kuvataso
  "Taso, joka hakee kuvan Harja palvelimelta"
  (:require [kuvataso.Lahde]
            [ol.Image]
            [ol.layer.Image]
            [ol.source.Image]
            [ol.source.ImageStatic]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :refer [karttakuva-url]]
            [harja.ui.openlayers.tasot :refer [Taso]]))

(defrecord Kuvataso [z-index extent selitteet source]
  Taso
  (aseta-z-index [this z-index]
    (assoc this :z-index z-index))
  (extent [this]
    extent)
  (selitteet [this]
    selitteet))

(defn- ol-kuva [extent resolution url]
  (ol.Image. extent resolution 1 nil url ""
             ol.source.Image/defaultImageLoadFunction))

(defn hae-fn [parametrit]
  (let [kuva (atom nil)]
    (fn [extent resolution pixel-ratio projection]
      (second
       (swap! kuva
              (fn [[url image]]
                (let [[x1 y1 x2 y2] extent
                      uusi-url (karttakuva-url
                                (concat  ["x1" x1 "y1" y1 "x2" x2 "y2" y2
                                          "r" resolution "pr" pixel-ratio]
                                         parametrit))]
                  (if (= uusi-url url)
                    [url image]
                    (do (log "UUSI KUVA URL: " uusi-url)
                        [uusi-url
                         (ol-kuva extent resolution uusi-url)])))))))))


(defn luo-kuvataso [projection extent parametrit]
  (->Kuvataso nil nil nil
              (kuvataso.Lahde. (hae-fn parametrit)
                               #js {:projection projection
                                    :imageExtent extent})))
#_(doto (ol.layer.Image.
       (clj->js {:source source
                 :wrapX true}))
  (.setZIndex 99))
