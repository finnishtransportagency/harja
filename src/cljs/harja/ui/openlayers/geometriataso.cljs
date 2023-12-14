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

(defn- tee-geometria-taso
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

(defn tee-heatmap-layer [ominaisuudet]
  (let [source (ol.source.Vector. #js {:features (clj->js ominaisuudet)})
        heatmap-layer (ol.layer.Heatmap. #js {:source source
                                              :blur 22
                                              :radius 3})] ;; 28 & 4 myös aika hyvä jos haluaa hieman isompana  
    heatmap-layer))

(defn heatmap-pisteet [heatmap-koordinaatit]
  (map (fn [coords]
         (ol.Feature. (ol.geom.Point.  (clj->js coords))))
    #_[[367320.5130002269 7244045.459997045] [367350.7086205464 7250511.133134752]]
    heatmap-koordinaatit)) ; Oulu  

(defn paivita-ol3-tason-geometriat
  "Kun annetaan tason vektori, nykyisten geometrioiden map ja 
   uusien geometrioiden sequenssi, päivittää (luo/poistaa) geometriat
   tasossa vastaamaan uusia kohteita. Palauttaa uuden vektorin päivitysten kanssa (taso & geometria map)
   Jos saapuvan tason ja vectorin arvo on nil, luodaan uusi ol3-taso."
  [ol3 geometria-taso geometria-map items]
  (let [tee-uusi-taso? (nil? geometria-taso)
        heatmap-koordinaatit (mapcat (fn [item]
                                       (when (= (:type item) :heatmap)
                                         (mapcat :points (:lines (:alue item)))))
                               items)
        heatmap? (= (-> items first :type) :heatmap)
        heatmap (tee-heatmap-layer (heatmap-pisteet heatmap-koordinaatit))
        geometria-taso (if tee-uusi-taso?
                         (doto
                           (if heatmap?
                             heatmap
                             (tee-geometria-taso (taso/opacity items)))
                           (.setZIndex (or (:zindex (meta items)) 0)))
                         geometria-taso)
        geometria-map (if tee-uusi-taso? {} geometria-map)
        geometries-set (into #{} items)
        ominaisuudet (.getSource geometria-taso)]

    (when tee-uusi-taso?
      (.addLayer ol3 geometria-taso))

    (if heatmap?
      ;; Jos kyseessä heatmap taso, se on jo käsitelty, palauta taso ja geometriat
      [geometria-taso items]
      ;; Muussa tapauksessa käsitellään uudet geometriat
      (do
        ;; Poista kaikki ol3-ominaisuusobjektit jotka eivät enää ole uusissa geometrioissa
        (doseq [[avain feature] (seq geometria-map)
                :when (and feature (not (geometries-set avain)))]
          (.removeFeature ominaisuudet feature))

        (loop [uudet-geometriat {} [item & items] items]
          (if-not item
            ;; Kun kaikki kohteet on käsitelty, palauta taso ja uusi geometria mappi
            [geometria-taso uudet-geometriat]
            ;; Luo ominaisuudet uusille geometrioille ja päivitä geometriat
            (let [alue (:alue item)]
              (recur
                (assoc uudet-geometriat item
                  (or (geometria-map item)
                    (when-let [new-shape (luo-feature alue)]
                      (aseta-feature-geometria! new-shape item)
                      (.addFeature ominaisuudet new-shape)

                      ;; Aseta geneerinen tyyli tyypeille,
                      ;; joiden luo-feature ei sitä tee
                      (when (tyyppi-tarvitsee-tyylit (:type alue))
                        (featuret/aseta-tyylit new-shape alue))

                      new-shape)))
                items))))))))

;; Laajenna vektorit olemaan tasoja
(extend-protocol Taso
  PersistentVector
  (aseta-z-index [this z-index]
    (with-meta this
      (merge (meta this) {:zindex z-index})))
  (extent [this]
    (-> this meta :extent))
  (opacity [this]
    (or (-> this meta :opacity) 1))
  (selitteet [this]
    (-> this meta :selitteet))
  (aktiivinen? [this]
    (some? (taso/extent this)))
  (paivita [items ol3 ol-layer aiempi-paivitystieto]
    (paivita-ol3-tason-geometriat ol3 ol-layer aiempi-paivitystieto items))
  (hae-asiat-pisteessa [this koordinaatti extent]
    (let [ch (async/chan)]
      (if-let [hae-asiat (-> this meta :hae-asiat)]
        (go
          (doseq [asia (hae-asiat koordinaatti)]
            (>! ch asia))
          (async/close! ch))
        (async/close! ch))
      ch)))
