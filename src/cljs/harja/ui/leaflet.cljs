(ns harja.ui.leaflet
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan timeout] :as async]
            [harja.tiedot.navigaatio :as nav]
            
  )
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; Kanava, jolla voidaan komentaa karttaa
(def komento-ch (chan))

(defn fit-bounds! [geometry]
  (go (>! komento-ch [::fit-bounds geometry])))

(defn show-popup! [lat-lng content]
  (go (>! komento-ch [::popup lat-lng content])))

;;;;;;;;;
;; Define the React lifecycle callbacks to manage the LeafletJS
;; Javascript objects.

(declare update-leaflet-geometries)


(def ^:export the-kartta (atom nil))

(defn ^:export invalidate-size []
  (.invalidateSize @the-kartta))

(def geometria-avain "Funktio, joka muuntaa geometrian tiedon avaimeksi mäppiä varten."
  (juxt :type :id))

(defn- leaflet-did-mount [this]
  "Initialize LeafletJS map for a newly mounted map component."
  (let [mapspec (:mapspec (reagent/state this))
        leaflet (js/L.Map. (:id mapspec)
                           (clj->js {:scrollWheelZoom false}))
        _ (reset! the-kartta leaflet)
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
          ::fit-bounds (let [{:keys [leaflet geometries-map]} (reagent/state this)
                             avain (geometria-avain (first args))
                             g (geometries-map avain)]
                         (log "löytyi geometrioista? " (pr-str avain) " => " g)
                         (doseq [k (keys geometries-map)]
                           (log "GEOMETRIA: " (pr-str k)))
                         (when g
                           (log "FIDBOUNDS kutsuttu")
                           (.fitBounds leaflet g)))
          ::popup (let [[[lat lng] content] args
                        elt (js/document.createElement "div")
                        comp (reagent/render content elt)]
                    (log "ELEMENTTI: " elt " , COMP: " comp)
                    (log "NÄYTÄ POPUP " lat ", " lng "  :: " content)
                    (.openPopup leaflet
                                (doto (js/L.popup)
                                  (.setLatLng (js/L.LatLng. lat lng))
                                  (.setContent  elt #_(reagent/render-to-string content)))))
          :default (log "tuntematon kartan komento: " komento))
        (recur (alts! [komento-ch unmount-ch]))))
    
    ;; Leaflet voi jäädä jumiin, jos kartan DOM elementtiä muuttelee eikä kerro siitä
    (add-watch nav/kartan-koko ::paivita-kartan-koko
               (fn [& _]
                 (log "LEAFLET: KARTAN KOKO MUUTTUI")
                 (js/setTimeout #(.invalidateSize leaflet) 100)))
               
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
                             :geometries-map {}
                             :hover nil
                             :unmount-ch unmount-ch})

    ;; If mapspec defines callbacks, bind them to leaflet
    (when-let [on-click (:on-click mapspec)]
      (.on leaflet "click" (fn [e]
                             (on-click [(-> e .-latlng .-lat) (-> e .-latlng .-lng)]))))

    ;; Add callback for leaflet pos/zoom changes
    ;; watcher for pos/zoom atoms
    (.on leaflet "move" (fn [e]
                          (let [c (.getCenter leaflet)]
                            (log "MOVE callback ja zoom on: " (.getZoom leaflet))
                            ;;(reset! zoom (.getZoom leaflet)) ;; FIXME: tämä heittelee zoomia miten sattuu (move eventissä zoom ei ole oikein)
                            (reset! view [(.-lat c) (.-lng c)]))))
    ;; TÄMÄ WATCHERI aiheuttaa nykimistä pannatessa
    ;;(add-watch view ::view-update
    ;,           (fn [_ _ old-view new-view]
    ;;             ;;(.log js/console "change view: " (clj->js old-view) " => " (clj->js new-view) @zoom)
    ;;             (when (not= old-view new-view)
    ;;              (.setView leaflet (clj->js new-view) @zoom))))
    (add-watch zoom ::zoom-update
               (fn [_ _ old-zoom new-zoom]
                 (.log js/console "zoom päivittyi: " old-zoom " => " new-zoom)
                 (when (not= old-zoom new-zoom)
                   (.setZoom leaflet new-zoom))))

    ;; Jos valittu item on olemassa, sovita kartta siihen kun valinta tehdään
    (when selection
      (add-watch selection ::valinta
                 (fn [_ _ _ item]
                   (let [{:keys [leaflet geometries-map]} (reagent/state this)]
                     (when-let [g (geometries-map (geometria-avain item))]
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

(defn leaflet-will-unmount [this]
  (let [{:keys [leaflet geometries-map unmount-ch]} (reagent/state this)]
    (log "LEAFLET: UNMOUNT")
    (async/close! unmount-ch)
    (doseq [shape (vals geometries-map)]
      (try
        (.remove leaflet shape)
        (catch :default _)))))
    
(defn- leaflet-will-update [this [_ conf]]
  (update-leaflet-geometries this (-> conf :geometries)))

(defn- leaflet-render [mapspec]
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

(defmethod create-shape :multiline [{:keys [lines color]}]
  (js/L.MultiPolyline. (clj->js (mapv :points lines))
                       #js {:color (or color "blue")}))

(defmethod create-shape :line [{:keys [points color]}]
  (js/L.Polyline. (clj->js points)
                  #js {:color (or color "blue")}))
                                    
(defn- update-leaflet-geometries [component items]
  "Update the LeafletJS layers based on the data, mutates the LeafletJS map object."
  ;;(.log js/console "geometries: " (pr-str items))
  (let [{:keys [leaflet geometries-map mapspec hover fit-bounds]} (reagent/state component)
        geometry-fn (or (:geometry-fn mapspec) identity)
        on-select (:on-select mapspec)
        geometries-set (into #{} (map geometria-avain) items)]
    ;; Remove all LeafletJS shape objects that are no longer in the new geometries
    (doseq [[avain shape] (seq geometries-map)
            :when (not (geometries-set avain))]
      (log "LEAFLET POISTA: " (pr-str avain))
      (.removeLayer leaflet shape))

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
            (let [shape (or (geometries-map avain)
                            (doto (create-shape geom)
                              (.on "click" #(on-select item %))
                              (.on "mouseover" #(do ;;(log "EVENTTI ON " %)
                                                  (reagent/set-state component
                                                                     {:hover (assoc item
                                                                               :x (aget % "containerPoint" "x")
                                                                               :y (aget % "containerPoint" "y"))
                                                                      })))
                              (.on "mouseout" #(reagent/set-state component {:hover nil}))
                              (.addTo leaflet)))]
              (log "LEAFLET: " (pr-str avain) " = " (pr-str geom))
              ;; If geometry has ::fit-bounds value true, then zoom to this
              ;; only 1 item should have this
              (if (and (::fit-bounds geom) (not= fit-bounds avain))
                (do
                  (go (<! (timeout 100))
                      (.fitBounds leaflet (.getBounds shape)))
                  (recur (assoc new-geometries-map avain shape) avain items))
                (recur (assoc new-geometries-map avain shape) new-fit-bounds items)))))))))



;;;;;;;;;
;; The LeafletJS Reagent component.

(defn leaflet [mapspec]
  "A LeafletJS map component."
  (reagent/create-class
    {:get-initial-state (fn [_] {:mapspec mapspec})
     :component-did-mount leaflet-did-mount
     :component-will-update leaflet-will-update
     :reagent-render leaflet-render
     :component-will-unmount leaflet-will-unmount}))


