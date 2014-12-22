(ns harja.ui.leaflet
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;;;;;;;;
;; Define the React lifecycle callbacks to manage the LeafletJS
;; Javascript objects.

(declare update-leaflet-geometries)


(defn- leaflet-did-mount [this]
  "Initialize LeafletJS map for a newly mounted map component."
  (let [mapspec (:mapspec (reagent/state this))
        leaflet (js/L.map (:id mapspec)
                          (clj->js {:scrollWheelZoom false}))
        view (:view mapspec)
        zoom (:zoom mapspec)
        selection (:selection mapspec)
        item-geometry (or (:geometry-fn mapspec) identity)]
      
    (.setView leaflet (clj->js @view) @zoom)
    (doseq [{:keys [type url] :as layer-spec} (:layers mapspec)]
      (let [layer (case type
                    :tile (js/L.tileLayer
                           url
                           (clj->js {:attribution (:attribution layer-spec)})
                                    )
                    :wms (js/L.tileLayer.wms
                          url
                          (clj->js {:format "image/png"
                                    :fillOpacity 1.0
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
                 (when (not= old-zoom new-zoom)
                   (.setZoom leaflet new-zoom))))

    ;; Jos valittu item on olemassa, sovita kartta siihen kun valinta tehdään
    (when selection
      (add-watch selection ::valinta
                 (fn [_ _ _ item]
                   (let [{:keys [leaflet geometries-map]} (reagent/state this)]
                     (.log js/console "valinta: " (pr-str geometria))
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
  (.log js/console "NEW geom: " (pr-str (:geometries conf)))
  (update-leaflet-geometries this (-> conf :geometries)))

(defn- leaflet-render [this]
  (let [mapspec (-> this reagent/state :mapspec)]
  [:div {:id (:id mapspec)
         :style {:width (:width mapspec)
                 :height (:height mapspec)}}]))

;;;;;;;;;;
;; Code to sync ClojureScript geometries vector data to LeafletJS
;; shape objects.

(defmulti create-shape :type)

(defmethod create-shape :polygon [{:keys [coordinates color]}]
  (js/L.polygon (clj->js coordinates)
                        #js {:color (or color "red")
                             :fillOpacity 0.5}))

(defmethod create-shape :line [{:keys [coordinates color]}]
  (js/L.polyline (clj->js coordinates)
                 #js {:color (or color "blue")}))

(defmethod create-shape :point [{:keys [coordinates color]}]
  (js/L.circle (clj->js (first coordinates))
               10
               #js {:color (or color "green")}))

(defn- update-leaflet-geometries [component items]
  "Update the LeafletJS layers based on the data, mutates the LeafletJS map object."
  (.log js/console "geometries: " (pr-str items))
  (let [{:keys [leaflet geometries-map mapspec]} (reagent/state component)
        geometry-fn (or (:geometry-fn mapspec) identity)
        on-select (:on-select mapspec)
        geometries-set (into #{} items)]
    ;; Remove all LeafletJS shape objects that are no longer in the new geometries
    (doseq [removed (keep (fn [[item shape]]
                          (when-not (geometries-set item)
                            shape))
                        geometries-map)]
      (.log js/console "Removed: " removed)
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
            (if-let [existing-shape (geometries-map item)]
              ;; Have existing shape, don't need to do anything
              (recur (assoc new-geometries-map item existing-shape) items)

              ;; No existing shape, create a new shape and add it to the map
              (let [shape (create-shape geom)]
                (when on-select
                  (.on shape "click" #(on-select item)))
                (.log js/console "Added: " (pr-str geom))
                (.addTo shape leaflet)
                (recur (assoc new-geometries-map item shape) items)))))))))



;;;;;;;;;
;; The LeafletJS Reagent component.

(defn leaflet [mapspec]
  "A LeafletJS map component."
  (reagent/create-class
    {:get-initial-state (fn [_] {:mapspec mapspec})
     :component-did-mount leaflet-did-mount
     :component-will-update leaflet-will-update
     :render leaflet-render}))

