(ns harja.ui.leaflet
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! timeout]]
  )
  (:require-macros [cljs.core.async.macros :refer [go]]))


;;;;;;;;;
;; Define the React lifecycle callbacks to manage the LeafletJS
;; Javascript objects.

(declare update-leaflet-geometries)


 
(defn- leaflet-did-mount [this]
  "Initialize LeafletJS map for a newly mounted map component."
  (let [mapspec (:mapspec (reagent/state this))
        leaflet (js/L.Map. (:id mapspec)
                           (clj->js {:scrollWheelZoom false}))
        view (:view mapspec)
        zoom (:zoom mapspec)
        selection (:selection mapspec)
        item-geometry (or (:geometry-fn mapspec) identity)]
      
    (.setView leaflet (clj->js @view) @zoom)
    (doseq [{:keys [type url] :as layer-spec} (:layers mapspec)]
      (let [layer (case type
                    :tile (js/L.TileLayer.
                           url
                           (clj->js {:attribution (:attribution layer-spec)})
                                    )
                    :wms (js/L.TileLayer.WMS.
                          url
                          (clj->js {:format (or (:format layer-spec) "image/png")
                                    :fillOpacity 1.0
                                    :layers (str/join "," (:layers layer-spec))
                                    :srs (:srs layer-spec)
                                    })))]
        ;;(.log js/console "L.tileLayer = " layer)
        (.addTo layer leaflet)))
    ;;(.log js/console "L.map = " leaflet)
    (reagent/set-state this {:leaflet leaflet
                             :geometries-map {}})

    ;; If mapspec defines callbacks, bind them to leaflet
    (when-let [on-click (:on-click mapspec)]
      (.on leaflet "click" (fn [e]
                             (on-click [(-> e .-latlng .-lat) (-> e .-latlng .-lng)]))))

    ;; Add callback for leaflet pos/zoom changes
    ;; watcher for pos/zoom atoms
    (.on leaflet "move" (fn [e]
                          (let [c (.getCenter leaflet)]
                            (reset! zoom (.getZoom leaflet))
                            (reset! view [(.-lat c) (.-lng c)]))))
    (add-watch view ::view-update
               (fn [_ _ old-view new-view]
                 ;;(.log js/console "change view: " (clj->js old-view) " => " (clj->js new-view) @zoom)
                 (when (not= old-view new-view)
                   (.setView leaflet (clj->js new-view) @zoom))))
    (add-watch zoom ::zoom-update
               (fn [_ _ old-zoom new-zoom]
                 ;;(.log js/console "zoom päivittyi: " old-zoom " => " new-zoom)
                 (when (not= old-zoom new-zoom)
                   (.setZoom leaflet new-zoom))))

    ;; Jos valittu item on olemassa, sovita kartta siihen kun valinta tehdään
    (when selection
      (add-watch selection ::valinta
                 (fn [_ _ _ item]
                   (let [{:keys [leaflet geometries-map]} (reagent/state this)]
                     (when-let [g (geometries-map item)]
                       ;; Löytyi Leaflet shape uudelle geometrialle
                       (.fitBounds leaflet  (.getBounds g)))))))
    
                     
    (update-leaflet-geometries this (:geometries mapspec))
    
    ;; If the mapspec has an atom containing geometries, add watcher
    ;; so that we update all LeafletJS objects
    ;;(when-let [g (:geometries mapspec)]
    ;;  (add-watch g ::geometries-update
    ;;             (fn [_ _ _ new-items]
    ;;               (update-leaflet-geometries this new-items))))
    ))

(defn- leaflet-will-update [this [_ conf]]
  (update-leaflet-geometries this (-> conf :geometries)))

(defn- leaflet-render [mapspec]
  [:div {:id (:id mapspec)
         :style {:width (:width mapspec)
                 :height (:height mapspec)}}])

;;;;;;;;;;
;; Code to sync ClojureScript geometries vector data to LeafletJS
;; shape objects.

(defmulti create-shape :type)

(defmethod create-shape :polygon [{:keys [coordinates color fill]}]
  (js/L.Polygon. (clj->js coordinates)
                 #js {:color (or color "red")
                      :fill fill
                      :fillOpacity 0.5}))

(defmethod create-shape :line [{:keys [coordinates color] :as line}]
  (js/L.Polyline. (clj->js coordinates)
                  #js {:color (or color "blue")}))

(defmethod create-shape :point [{:keys [coordinates color]}]
  (js/L.Circle. (clj->js (first coordinates))
                10
                #js {:color (or color "green")}))

(defmethod create-shape :multipolygon [{:keys [polygons color fill]}]
  (let [ps (clj->js (mapv :coordinates polygons))]
    ;;(.log js/console "multipoly: " ps)
    (js/L.MultiPolygon. ps #js {:color (or color "green")
                                :fill fill})))

(defn- update-leaflet-geometries [component items]
  "Update the LeafletJS layers based on the data, mutates the LeafletJS map object."
  ;;(.log js/console "geometries: " (pr-str items))
  (let [{:keys [leaflet geometries-map mapspec]} (reagent/state component)
        geometry-fn (or (:geometry-fn mapspec) identity)
        on-select (:on-select mapspec)
        geometries-set (into #{} items)]
    ;; Remove all LeafletJS shape objects that are no longer in the new geometries
    (doseq [removed (keep (fn [[item shape]]
                          (when-not (geometries-set item)
                            shape))
                        geometries-map)]
      ;;(.log js/console "Removed: " removed)
      (.removeLayer leaflet removed))

    ;; Create new shapes for new geometries and update the geometries map
    (loop [new-geometries-map {}
           [item & items] items]
      (if-not item
        ;; Update component state with the new geometries map
        (reagent/set-state component {:geometries-map new-geometries-map})
        (let [geom (geometry-fn item)]
          (if-not geom
            (recur new-geometries-map items)
            (let [shape (or (geometries-map item)
                            (doto (create-shape geom)
                              (.on "click" #(on-select item))
                              (.addTo leaflet)))]
              ;; If geometry has ::fit-bounds value true, then zoom to this
              ;; only 1 item should have this
              (when (::fit-bounds geom)
                (go (<! (timeout 100))
                    (.fitBounds leaflet (.getBounds shape))))
              (recur (assoc new-geometries-map item shape) items))))))))



;;;;;;;;;
;; The LeafletJS Reagent component.

(defn leaflet [mapspec]
  "A LeafletJS map component."
  (reagent/create-class
    {:get-initial-state (fn [_] {:mapspec mapspec})
     :component-did-mount leaflet-did-mount
     :component-will-update leaflet-will-update
     :reagent-render leaflet-render}))


