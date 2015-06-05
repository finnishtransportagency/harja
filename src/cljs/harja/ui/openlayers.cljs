(ns harja.ui.openlayers
  "OpenLayers 3 kartta."
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan timeout] :as async]

            [harja.ui.ikonit :as ikonit]
            
            [ol]
            [ol.Map]
            [ol.Attribution]
            [ol.layer.Tile]
            [ol.source.WMTS]
            [ol.tilegrid.WMTS]
            [ol.View]
            [ol.extent :as ol-extent]
            [ol.proj :as ol-proj]

            [ol.source.Vector]
            [ol.layer.Vector]
            [ol.Feature]
            [ol.geom.Polygon]
            [ol.geom.Point]
            [ol.geom.LineString]
            [ol.geom.MultiLineString]
            
            [ol.style.Style]
            [ol.style.Fill]
            [ol.style.Stroke]
            [ol.style.Icon]
            
            [ol.control :as ol-control]
            [ol.interaction :as ol-interaction]

            [ol.Overlay] ;; popup
            )
  
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; PENDING:
;; Tämä namespace kaipaa rakkautta... cleanup olisi tarpeen.
;; Alunperin englanniksi Leafletille tehty karttaimplementaatio on kopioitu
;; ja pala palalta portattu toiminnallisuutta ol3 päälle.


;; Kanava, jolla voidaan komentaa karttaa
(def komento-ch (chan))

(defn fit-bounds! [geometry]
  (go (>! komento-ch [::fit-bounds geometry])))

(defn show-popup! [lat-lng content]
  (go (>! komento-ch [::popup lat-lng content])))

(defn invalidate-size! []
  (go (>! komento-ch [::invalidate-size])))

;;;;;;;;;
;; Define the React lifecycle callbacks to manage the OpenLayers
;; Javascript objects.

(declare update-ol3-geometries)


(def ^:export the-kartta (atom nil))

(defn ^:export invalidate-size []
  (.invalidateSize @the-kartta))


(def suomen-extent
  "Suomalaisissa kartoissa olevan projektion raja-arvot."
  [-548576.000000, 6291456.000000, 1548576.000000, 8388608.000000])

(def projektio (ol-proj/Projection. #js {:code "EPSG:3067"
                                         :extent (clj->js suomen-extent)}))

(defn keskipiste
  "Laskee geometrian keskipisteen extent perusteella"
  [geometria]
  (let [[x1 y1 x2 y2] (.getExtent geometria)]
    [(+ x1 (/ (- x2 x1) 2))
     (+ y1 (/ (- y2 y1) 2))]))
  
(defn geometria-avain
  "Funktio, joka muuntaa geometrian tiedon avaimeksi mäppiä varten."
  [g]
  (str (:type g) "__" (:id g)))


(defn luo-tilegrid []
  (let [koko (/ (ol-extent/getWidth (.getExtent projektio)) 256)]
    (loop [resoluutiot []
           matrix-idt []
           i 0]
      (if (= i 16)
        (let [optiot (clj->js {:origin (ol-extent/getTopLeft (.getExtent projektio))
                               :resolutions (clj->js resoluutiot)
                               :matrixIds (clj->js matrix-idt)})]
          (log "TILEGRID OPTIOT: " optiot)
          (ol.tilegrid.WMTS. optiot))
        (recur (conj resoluutiot (/ koko (Math/pow 2 i)))
               (conj matrix-idt i)
               (inc i))))))
    

(defn- mml-wmts-layer [url layer]
  (ol.layer.Tile.
   #js {:opacity 0.8
        :source (ol.source.WMTS. #js {:attributions [(ol.Attribution. #js {:html "MML"})]
                                      :url url  ;; Tämä pitää olla nginx proxyssa
                                      :layer layer
                                      :matrixSet "ETRS-TM35FIN"
                                      :format "image/png"
                                      :projection projektio
                                      :tileGrid (luo-tilegrid)
                                      :style "default"
                                      :wrapX true})}))

(defn- tapahtuman-geometria
  "Hakee annetulle ol3 tapahtumalle geometrian. Jos monta löytyy, palauttaa viimeisen löytyneen."
  [this e]
  (let [geom (cljs.core/atom nil)
        {:keys [ol3 geometries-map]} (reagent/state this)]
    (.forEachFeatureAtPixel ol3 (.-pixel e)
                            (fn [feature layer]
                              (when-let [g (some-> geometries-map
                                                   (get (.getId feature))
                                                   second)]
                                (reset! geom g))))
    @geom))

(defn- aseta-klik-kasittelija [this ol3 on-click on-select]
  (.on ol3 "click" (fn [e]
                     (when on-click
                       (on-click e))
                     (when on-select
                       (when-let [g (tapahtuman-geometria this e)]
                         (on-select g e))))))

(defn aseta-hover-kasittelija [this ol3]
  (.on ol3 "pointermove" (fn [e]
                           (reagent/set-state this
                                              (if-let [g (tapahtuman-geometria this e)]
                                                {:hover (assoc g
                                                          :x (aget (.-pixel e) 0)
                                                          :y (aget (.-pixel e) 1))}
                                                {:hover nil})))))


(defn keskita!
  "Keskittää kartan näkymän annetun featureen sopivaksi."
  [ol3 feature]
  (let [view (.getView ol3)
        extent (.getExtent (.getGeometry feature))]
    (.fitExtent view extent (.getSize ol3))))

(defn- poista-popup!
  "Poistaa kartan popupin, jos sellainen on."
  [this]
  (let [{:keys [ol3 popup]} (reagent/state this)]
  (when popup
    (.removeOverlay ol3 popup)
    (reagent/set-state this {:popup nil}))))

(defn luo-overlay [koordinaatti sisalto]
  (let [elt (js/document.createElement "span")
        comp (reagent/render sisalto elt)]
    (ol.Overlay. (clj->js {:element elt
                           :position koordinaatti
                           :stopEvent false}))))

    
(defn- nayta-popup!
  "Näyttää annetun popup sisällön annetussa koordinaatissa. Mahdollinen edellinen popup poistetaan."
  [this koordinaatti sisalto]
  (let [{:keys [ol3 popup]} (reagent/state this)]
    (when popup
      (.removeOverlay ol3 popup))
    (let [popup (luo-overlay koordinaatti
                             [:div.ol-popup
                              [:a.ol-popup-closer {:on-click #(do
                                                                (.stopPropagation %)
                                                                (.preventDefault %)
                                                                (poista-popup! this))}]
                              sisalto])]
      (.addOverlay ol3 popup)
      (reagent/set-state this {:popup popup}))))

(defn- ol3-did-mount [this]
  "Initialize OpenLayers map for a newly mounted map component."
  (let [mapspec (:mapspec (reagent/state this))
        [mml-spec & _] (:layers mapspec)
        mml (mml-wmts-layer (:url mml-spec) (:layer mml-spec))
        geometry-layer  (ol.layer.Vector. #js {:source (ol.source.Vector.) :opacity 0.7})
        map-optiot (clj->js {:layers  [mml geometry-layer]
                             :target (:id mapspec)
                             :controls (ol-control/defaults #js {})
                             :interactions (ol-interaction/defaults #js {:mouseWheelZoom false})})
        ol3 (ol/Map. map-optiot)
                          
        _ (reset! the-kartta ol3) ;; puhtaasi REPL tunkkausta varten
        view (:view mapspec)
        zoom (:zoom mapspec)
        selection (:selection mapspec)
        item-geometry (or (:geometry-fn mapspec) identity)
        unmount-ch (chan)]

    ;; Aloitetaan komentokanavan kuuntelu
    (go-loop [[[komento & args] ch] (alts! [komento-ch unmount-ch])]
      (when-not (= ch unmount-ch)
        (log "TULI KOMENTO " komento) 
        (case komento
          ::fit-bounds (let [{:keys [ol3 geometries-map]} (reagent/state this)
                             view (.getView ol3)
                             avain (geometria-avain (first args))
                             [g _] (geometries-map avain)]
                         (when g
                           (keskita! ol3 g)))
          ::popup (let [[coordinate content] args]
                    (nayta-popup! this coordinate content))
          
          ::invalidate-size (.updateSize ol3)
          
          :default (log "tuntematon kartan komento: " komento))
        (recur (alts! [komento-ch unmount-ch]))))
    
    (.setView ol3 (ol.View. #js {:center (clj->js @view)
                                 :zoom @zoom}))
    
    ;;(.log js/console "L.map = " ol3)
    (reagent/set-state this {:ol3 ol3
                             :geometries-map {}
                             :geometry-layer geometry-layer
                             :hover nil
                             :unmount-ch unmount-ch})

    ;; If mapspec defines callbacks, bind them to ol3
    (aseta-klik-kasittelija this ol3 (:on-click mapspec) (:on-select mapspec))
    (aseta-hover-kasittelija this ol3)
    ;; Add callback for ol3 pos/zoom changes
    ;; watcher for pos/zoom atoms
    (.on ol3 "move" (fn [e]
                      (log "liikkui " e)
                      (let [c (.getCenter ol3)]
                        (log "MOVE callback ja zoom on: " (.getZoom ol3))
                        ;;(reset! zoom (.getZoom ol3)) ;; FIXME: tämä heittelee zoomia miten sattuu (move eventissä zoom ei ole oikein)
                        (reset! view [(.-lat c) (.-lng c)]))))
    ;; TÄMÄ WATCHERI aiheuttaa nykimistä pannatessa
    ;;(add-watch view ::view-update
    ;,           (fn [_ _ old-view new-view]
    ;;             ;;(.log js/console "change view: " (clj->js old-view) " => " (clj->js new-view) @zoom)
    ;;             (when (not= old-view new-view)
    ;;              (.setView ol3 (clj->js new-view) @zoom))))
    (add-watch zoom ::zoom-update
               (fn [_ _ old-zoom new-zoom]
                 (let [view (.getView ol3)]
                   (.log js/console "zoom päivittyi: " old-zoom " => " new-zoom)
                   (when (not= (.getZoom view) new-zoom)
                     (.setZoom view new-zoom)))))
    
    (update-ol3-geometries this (:geometries mapspec))
    
    ;; If the mapspec has an atom containing geometries, add watcher
    ;; so that we update all Ol3JS objects
    ;;(when-let [g (:geometries mapspec)]
    ;;  (add-watch g ::geometries-update
    ;;             (fn [_ _ _ new-items]
    ;;               (update-ol3-geometries this new-items))))
    ))

(defn ol3-will-unmount [this]
  (let [{:keys [ol3 geometries-map unmount-ch]} (reagent/state this)]
    (log "OL3: UNMOUNT")
    
    (async/close! unmount-ch)))
    
(defn- ol3-will-update [this [_ conf]]
  (update-ol3-geometries this (-> conf :geometries)))

(defn- ol3-render [mapspec]
  (let [c (reagent/current-component)]
    [:span 
     [:div {:id (:id mapspec)
            :style (merge {:width (:width mapspec)
                           :height (:height mapspec)}
                          (:style mapspec))}]
     (when-let [t (:tooltip-fn mapspec)]
       (when-let [hover (-> c reagent/state :hover)]
         (go (<! (timeout 1000))
             (when (= hover (:hover (reagent/state c)))
               (reagent/set-state c {:hover nil})))
         [:div.kartta-tooltip {:style {:left (+ 20 (:x hover)) :top (+ 10 (:y hover))}}
          (t hover)]))]))

;;;;;;;;;;
;; Code to sync ClojureScript geometries vector data to Ol3JS
;; shape objects.

;; ol.source.Vector on lähteenä ol.layer.Vector tasolle
;; ol.source.Vector.addFeature(f)/removeFeature(f)/getFeatureById(string)
;; 

(defmulti luo-feature :type)

(defn- aseta-tyylit [feature {:keys [color fill stroke marker] :as geom}]
  (doto feature
    (.setStyle (ol.style.Style.
                #js {:fill (when fill (ol.style.Fill. #js {:color (or color "red")
                                                           :opacity 0.5}))
                     :stroke (ol.style.Stroke. #js {:color (or (:color stroke) "black")
                                                    :width (or (:width stroke) 1)})}))))


(defmethod luo-feature :polygon [{:keys [coordinates] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.Polygon. (clj->js [coordinates]))}))


(defmethod luo-feature :line [{:keys [coordinates color] :as line}]
  (ol.Feature. #js {:geometry (ol.geom.LineString. (clj->js coordinates))}))


(comment
  (defmethod luo-feature :point [{:keys [coordinates color]}]
    (js/L.Circle. (clj->js (first coordinates))
                  10
                  #js {:color (or color "green")})))

(defmethod luo-feature :multipolygon [{:keys [polygons] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.Polygon. (clj->js (mapv :coordinates polygons)))}))

(defmethod luo-feature :multiline [{:keys [lines] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.MultiLineString. (clj->js (mapv :points lines)))}))


(defmethod luo-feature :line [{:keys [points] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.LineString. (clj->js points))}))

(defn luo-marker [geometry]
  (doto (ol.Feature. #js {:geometry (ol.geom.Point. (clj->js (keskipiste geometry)))})
    (.setStyle (ol.style.Style. #js {:image (ol.style.Icon. #js {:src "images/marker.png"
                                                                 :anchor #js [0.5 35]
                                                                 :anchorXUnits "fraction"
                                                                 :anchorYUnits "pixels"})}))))

                                    
(defn- update-ol3-geometries [component items]
  "Update the Ol3JS layers based on the data, mutates the Ol3JS map object."
  ;;(.log js/console "geometries: " (pr-str items))
  (let [{:keys [ol3 geometries-map geometry-layer mapspec hover fit-bounds]} (reagent/state component)
        geometry-fn (or (:geometry-fn mapspec) identity)
        geometries-set (into #{} (map geometria-avain) items)
        features (.getSource geometry-layer)]

    ;;(log "GEOMETRY layer: " geometry-layer)
    ;; Remove all Ol3JS shape objects that are no longer in the new geometries
    (doseq [[avain [shape _]] (seq geometries-map) 
            :when (not (geometries-set avain))]
      (log "OL3 POISTA: " (pr-str avain))
      (.removeFeature features shape))

    ;; Create new shapes for new geometries and update the geometries map
    (loop [new-geometries-map {}
           new-fit-bounds fit-bounds
           [item & items] items]
      (if-not item
        ;; Update component state with the new geometries map
        (reagent/set-state component {:geometries-map new-geometries-map
                                      :fit-bounds new-fit-bounds})
          
        (let [geom (geometry-fn item)
              avain (geometria-avain item)]
          (if-not geom
            (recur new-geometries-map new-fit-bounds items)
            (let [shape (or (first (geometries-map avain))
                            (let [new-shape  (luo-feature geom)]
                              (.setId new-shape avain)
                              (.addFeature features new-shape)

                              ;; FIXME: markereille pitää miettiä joku tapa, otetaanko ne new-geometries-map mukaan?
                              ;; vai pitääkö ne antaa suoraan geometrian tyyppinä?
                              #_(when (:marker geom)
                                  (.addOverlay ol3 (luo-overlay (keskipiste (.getGeometry new-shape))
                                                                [:div {:style {:font-size "200%"}} (ikonit/map-marker)])))
                              new-shape))]
              (aseta-tyylit shape geom)
              ;;(log "OL3: " (pr-str avain) " = " (pr-str geom))
              ;; If geometry has ::fit-bounds value true, then zoom to this
              ;; only 1 item should have this
              (if (and (::fit-bounds geom) (not= fit-bounds avain))
                (do
                  (go (<! (timeout 100))
                      (keskita! ol3 shape))
                  (recur (assoc new-geometries-map avain [shape item]) avain items))
                (recur (assoc new-geometries-map avain [shape item]) new-fit-bounds items)))))))))


;;;;;;;;;
;; The OpenLayers 3 Reagent component.

(defn openlayers [mapspec]
  "A OpenLayers map component."
  (reagent/create-class
   {:get-initial-state (fn [_] {:mapspec mapspec})
    :component-did-mount ol3-did-mount
    :component-will-update ol3-will-update
    :reagent-render ol3-render
    :component-will-unmount ol3-will-unmount}))



  
