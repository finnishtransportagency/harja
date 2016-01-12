(ns harja.ui.openlayers
  "OpenLayers 3 kartta."
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan timeout] :as async]

            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.animaatio :as animaatio]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.geo :as geo]
            
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
            [ol.geom.Circle]
            [ol.geom.LineString]
            [ol.geom.MultiLineString]

            [ol.style.Style]
            [ol.style.Fill]
            [ol.style.Stroke]
            [ol.style.Icon]

            [ol.control :as ol-control]
            [ol.interaction :as ol-interaction]

            [ol.Overlay]                                    ;; popup
            [harja.virhekasittely :as vk]
            [harja.asiakas.tapahtumat :as t])

  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [harja.makrot :refer [nappaa-virhe]]
                   [harja.loki :refer [mittaa-aika]]))

(def ^{:doc "Odotusaika millisekunteina, joka odotetaan että kartan animoinnit on valmistuneet." :const true}
  animaation-odotusaika 200)

(def ^{:doc "ol3 näkymän resoluutio alkutilanteessa" :const true}
  initial-resolution 1200)

(def ^{:doc "Suurin mahdollinen zoom-taso, johon käyttäjä voi zoomata sisään" :const true}
  min-zoom 5)
(def ^{:doc "Pienin mahdollinen zoom-taso, johon käyttäjä voi zoomata ulos" :const true}
  max-zoom 20)


;; Näihin atomeihin voi asettaa oman käsittelijän kartan
;; klikkauksille ja hoveroinnille. Jos asetettu, korvautuu
;; kartan normaali toiminta.
;; Nämä ovat normaaleja cljs atomeja, eivätkä siten voi olla reagent riippuvuuksia.
(defonce klik-kasittelija (cljs.core/atom nil))
(defonce hover-kasittelija (cljs.core/atom nil))

(defn aseta-klik-kasittelija! [funktio]
  (reset! klik-kasittelija funktio))
(defn poista-klik-kasittelija! []
  (aseta-klik-kasittelija! nil))
(defn aseta-hover-kasittelija! [funktio]
  (reset! hover-kasittelija funktio))
(defn poista-hover-kasittelija! []
  (aseta-hover-kasittelija! nil))

(def +karttaikonipolku+ "images/karttaikonit/")

;; Kanava, jolla voidaan komentaa karttaa
(def komento-ch (chan))


(defn show-popup! [lat-lng content]
  (go (>! komento-ch [::popup lat-lng content])))

(defn hide-popup! []
  (go (>! komento-ch [::hide-popup])))

(defn hide-popup-without-event! []
  (go (>! komento-ch [::hide-popup-without-event])))

(defn invalidate-size! []
  (go (>! komento-ch [::invalidate-size])))

(defn aseta-kursori! [kursori]
  (go (>! komento-ch [::cursor kursori])))

(defn aseta-tooltip! [x y teksti]
  (go (>! komento-ch [::tooltip x y teksti])))

;;;;;;;;;
;; Define the React lifecycle callbacks to manage the OpenLayers
;; Javascript objects.

(declare update-ol3-geometries)


(def ^:export the-kartta (atom nil))

(defn set-map-size! [w h]
  (.setSize @the-kartta (clj->js [w h])))

(defn keskita-kartta-pisteeseen! [keskipiste]
  (when-let [ol3 @the-kartta]
    (.setCenter (.getView ol3) (clj->js keskipiste))))

(defn keskita-kartta-alueeseen! [alue]
  (assert (vector? alue) "Alueen tulee vektori numeroita")
  (assert (= 4 (count alue)) "Alueen tulee olla vektori [minx miny maxx maxy]")
  (when-let [ol3 @the-kartta]
    (let [view (.getView ol3)]
      (.fit view (clj->js alue) (.getSize ol3)))))

(defn extent-sisaltaa-extent? [iso pieni]
  (assert (and (vector? iso) (vector? pieni)) "Alueen tulee vektori numeroita")
  (assert (and (= 4 (count iso)) (= 4 (count pieni))) "Alueen tulee olla vektori [minx miny maxx maxy]")

  (ol/extent.containsExtent (clj->js iso) (clj->js pieni)))

(defn ^:export debug-keskita [x y]
  (keskita-kartta-pisteeseen! [x y]))

(defn ^:export invalidate-size []
  (.invalidateSize @the-kartta))

(defn kartan-extent []
  (let [k @the-kartta]
    (.calculateExtent (.getView k) (.getSize k))))

(defonce openlayers-kartan-leveys (atom nil))

(def suomen-extent
  "Suomalaisissa kartoissa olevan projektion raja-arvot."
  ;; FIXME: tarkista, tämä näyttää olevan aivan liian laaja alue
  [-548576.000000, 6291456.000000, 1548576.000000, 8388608.000000])

(def projektio (ol-proj/Projection. #js {:code   "EPSG:3067"
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
  (identity g))


(defn luo-tilegrid []
  (let [koko (/ (ol-extent/getWidth (.getExtent projektio)) 256)]
    (loop [resoluutiot []
           matrix-idt []
           i 0]
      (if (= i 16)
        (let [optiot (clj->js {:origin      (ol-extent/getTopLeft (.getExtent projektio))
                               :resolutions (clj->js resoluutiot)
                               :matrixIds   (clj->js matrix-idt)})]
          (ol.tilegrid.WMTS. optiot))
        (recur (conj resoluutiot (/ koko (Math/pow 2 i)))
               (conj matrix-idt i)
               (inc i))))))


(defn- mml-wmts-layer [url layer]
  (ol.layer.Tile.
    #js {:source (ol.source.WMTS. #js {:attributions [(ol.Attribution. #js {:html "MML"})]
                                       :url          url    ;; Tämä pitää olla nginx proxyssa
                                       :layer        layer
                                       :matrixSet    "ETRS-TM35FIN"
                                       :format       "image/png"
                                       :projection   projektio
                                       :tileGrid     (luo-tilegrid)
                                       :style        "default"
                                       :wrapX        true})}))

(defn feature-geometria [feature]
  (.get feature "harja-geometria"))

(defn aseta-feature-geometria! [feature geometria]
  (.set feature "harja-geometria" geometria))


(defn- tapahtuman-geometria
  "Hakee annetulle ol3 tapahtumalle geometrian. Palauttaa ensimmäisen löytyneen geometrian."
  [this e]
  (let [geom (volatile! nil)
        {:keys [ol3 geometry-layers]} (reagent/state this)]
    (.forEachFeatureAtPixel ol3 (.-pixel e)
                            (fn [feature layer]
                              (vreset! geom (feature-geometria feature))
                              true))
    @geom))

(defn- laske-kartan-alue [ol3]
  (.calculateExtent (.getView ol3) (.getSize ol3)))

(defn- tapahtuman-kuvaus
  "Tapahtuman kuvaus ulkoisille käsittelijöille"
  [e]
  (let [c (.-coordinate e)
        tyyppi (.-type e)]
    {:tyyppi   (case tyyppi
                 "pointermove" :hover
                 "click" :click
                 "singleclick" :click)
     :sijainti [(aget c 0) (aget c 1)]
     :x        (aget (.-pixel e) 0)
     :y        (aget (.-pixel e) 1)}))

(defn- aseta-zoom-kasittelija [this ol3 on-zoom]
  (.on (.getView ol3) "change:resolution" (fn [e]
                                            (when on-zoom
                                              (on-zoom e (laske-kartan-alue ol3))))))

(defn- aseta-drag-kasittelija [this ol3 on-move]
  (.on ol3 "pointerdrag" (fn [e]
                           (when on-move
                             (on-move e (laske-kartan-alue ol3))))))

(defn- aseta-klik-kasittelija [this ol3 on-click on-select]
  (.on ol3 "singleclick"
       (fn [e]
         (if-let [kasittelija @klik-kasittelija]
           (kasittelija (tapahtuman-kuvaus e))
           (do (when on-click
                 (on-click e))
               (when on-select
                 (when-let [g (tapahtuman-geometria this e)]
                   (on-select g e))))))))

;; dblclick on-clickille ei vielä tarvetta - zoomaus tulee muualta.
(defn- aseta-dblclick-kasittelija [this ol3 on-click on-select]
  (.on ol3 "dblclick" (fn [e]
                        (when on-select
                          (when-let [g (tapahtuman-geometria this e)]
                            (on-select g e))))))


(defn aseta-hover-kasittelija [this ol3]
  (.on ol3 "pointermove"
       (fn [e]
         (if-let [kasittelija @hover-kasittelija]
           (kasittelija (tapahtuman-kuvaus e))

           (reagent/set-state this
                              (if-let [g (tapahtuman-geometria this e)]
                                {:hover (assoc g
                                          :x (aget (.-pixel e) 0)
                                          :y (aget (.-pixel e) 1))}
                                {:hover nil}))))))


(defn keskita!
  "Keskittää kartan näkymän annetun featureen sopivaksi."
  [ol3 feature]
  (let [view (.getView ol3)
        extent (.getExtent (.getGeometry feature))]
    (.fit view extent (.getSize ol3))))

(defn- poista-openlayers-popup!
  "Älä käytä tätä suoraan, vaan kutsu poista-popup! tai poista-popup-ilman-eventtia!"
  [this]
  (let [{:keys [ol3 popup]} (reagent/state this)]
    (when popup
      (.removeOverlay ol3 popup)
      (reagent/set-state this {:popup nil}))))

(defn- poista-popup!
  "Poistaa kartan popupin, jos sellainen on."
  [this]
  (t/julkaise! {:aihe :popup-suljettu})
  (poista-openlayers-popup! this))

(defn- poista-popup-ilman-eventtia!
  "Poistaa kartan popupin, jos sellainen on, eikä julkaise popup-suljettu eventtiä."
  [this]
  (poista-openlayers-popup! this))

(defn luo-overlay [koordinaatti sisalto]
  (let [elt (js/document.createElement "span")
        comp (reagent/render sisalto elt)]
    (ol.Overlay. (clj->js {:element   elt
                           :position  koordinaatti
                           :stopEvent false}))))


(defn- nayta-popup!
  "Näyttää annetun popup sisällön annetussa koordinaatissa. Mahdollinen edellinen popup poistetaan."
  [this koordinaatti sisalto]
  (let [{:keys [ol3 popup]} (reagent/state this)]
    (when popup
      (.removeOverlay ol3 popup))
    (let [popup (luo-overlay koordinaatti
                             [:div.ol-popup
                              [:a.ol-popup-closer.klikattava {:on-click #(do
                                                                          (.stopPropagation %)
                                                                          (.preventDefault %)
                                                                          (poista-popup! this))}]
                              sisalto])]
      (.addOverlay ol3 popup)
      (reagent/set-state this {:popup popup}))))

;; Käytetään the-karttaa joka oli aiemmin "puhtaasti REPL-tunkkausta varten"
(defn nykyinen-zoom-taso []
  (some-> @the-kartta (.getView) (.getZoom)))

(defn aseta-zoom [zoom]
  (some-> @the-kartta (.getView) (.setZoom zoom)))

(defn- create-geometry-layer
  "Create a new ol3 Vector layer with a vector source."
  []
  (ol.layer.Vector. #js {:source (ol.source.Vector.)}))

(defn- ol3-did-mount [this]
  "Initialize OpenLayers map for a newly mounted map component."
  (let [mapspec (:mapspec (reagent/state this))
        [mml-spec & _] (:layers mapspec)
        mml (mml-wmts-layer (:url mml-spec) (:layer mml-spec))
        interaktiot (let [oletukset (ol-interaction/defaults #js {:mouseWheelZoom false
                                                                  :dragPan        false})]
                      (.push oletukset (ol-interaction/DragPan. #js {})) ; ei kinetic-ominaisuutta!
                      oletukset)
        map-optiot (clj->js {:layers       [mml]
                             :target       (:id mapspec)
                             :controls     (ol-control/defaults #js {})
                             :interactions interaktiot})
        ol3 (ol/Map. map-optiot)

        _ (reset!
            openlayers-kartan-leveys
            (.-offsetWidth (aget (.-childNodes (reagent/dom-node this)) 0)))
        _ (reset! the-kartta ol3)
        extent (:extent mapspec)
        selection (:selection mapspec)
        item-geometry (or (:geometry-fn mapspec) identity)
        unmount-ch (chan)]

    ;; Lisää kartan animoinnin jälkeinen updateSize kutsu
    (when (animaatio/transition-end-tuettu?)
      (animaatio/kasittele-transition-end (.getElementById js/document (:id mapspec))
                                          #(.updateSize ol3)))

    ;; Aloitetaan komentokanavan kuuntelu
    (go-loop [[[komento & args] ch] (alts! [komento-ch unmount-ch])]
             (when-not (= ch unmount-ch)
               (nappaa-virhe
                (case komento
                  
                  ::popup
                  (let [[coordinate content] args]
                    (nayta-popup! this coordinate content))

                  ::invalidate-size
                  (do
                    (.updateSize ol3)
                    (.render ol3))

                  ::hide-popup
                  (poista-popup! this)

                  ::hide-popup-without-event
                  (poista-popup-ilman-eventtia! this)

                  ::cursor
                  (let [[cursor] args
                        vp (.-viewport_ ol3)
                        style (.-style vp)]
                    (set! (.-cursor style) (case cursor
                                             :crosshair "crosshair" ;; lisää tarvittavia kursoreita
                                             "")))
                  ::tooltip
                  (let [[x y teksti] args]
                    (reagent/set-state this
                                       {:hover {:x x :y y :tooltip teksti}}))))
               (recur (alts! [komento-ch unmount-ch]))))

    (.setView ol3 (ol.View. #js {:center  (clj->js (geo/extent-keskipiste extent))
                                 :resolution initial-resolution
                                 :maxZoom max-zoom
                                 :minZoom min-zoom}))

    ;;(.log js/console "L.map = " ol3)
    (reagent/set-state this {:ol3            ol3
                             :geometry-layers {} ;; key => vector layer
                             :hover          nil
                             :unmount-ch     unmount-ch})

    ;; If mapspec defines callbacks, bind them to ol3
    (aseta-klik-kasittelija this ol3 (:on-click mapspec) (:on-select mapspec))
    (aseta-dblclick-kasittelija this ol3 (:on-dblclick mapspec) (:on-dblclick-select mapspec))
    (aseta-hover-kasittelija this ol3)
    (aseta-drag-kasittelija this ol3 (:on-drag mapspec))
    (aseta-zoom-kasittelija this ol3 (:on-zoom mapspec))

    (update-ol3-geometries this (:geometries mapspec))

    (when-let [mount (:on-mount mapspec)]
      (mount (laske-kartan-alue ol3)))

    (tapahtumat/julkaise! {:aihe :kartta-nakyy})))

(defn ol3-will-unmount [this]
  (let [{:keys [ol3 geometries-map unmount-ch]} (reagent/state this)]
    (async/close! unmount-ch)))

(defn- ol3-did-update [this _]
  (let [uusi-leveys (.-offsetWidth (aget (.-childNodes (reagent/dom-node this)) 0))]
    (when-not (= uusi-leveys
                 @openlayers-kartan-leveys)
      (reset! openlayers-kartan-leveys uusi-leveys)
      (invalidate-size!))))

(defn- ol3-render [mapspec]
  (let [c (reagent/current-component)]
    [:span
     [:div {:id    (:id mapspec)
            :class (:class mapspec)
            :style (merge {:width  (:width mapspec)
                           :height (:height mapspec)}
                          (:style mapspec))}]
     (when-let [t (:tooltip-fn mapspec)]
       (when-let [hover (-> c reagent/state :hover)]
         (go (<! (timeout 1000))
             (when (= hover (:hover (reagent/state c)))
               (reagent/set-state c {:hover nil})))
         [:div.kartta-tooltip {:style {:left (+ 20 (:x hover)) :top (+ 10 (:y hover))}}
          (or (:tooltip hover)
              (t hover))]))]))

;;;;;;;;;;
;; Code to sync ClojureScript geometries vector data to Ol3JS
;; shape objects.

;; ol.source.Vector on lähteenä ol.layer.Vector tasolle
;; ol.source.Vector.addFeature(f)/removeFeature(f)/getFeatureById(string)
;; 

(defmulti luo-feature :type)

(defn- aseta-tyylit [feature {:keys [fill color stroke marker zindex] :as geom}]
  (doto feature
    (.setStyle (ol.style.Style.
                 #js {:fill   (when fill (ol.style.Fill. #js {:color (or color "red")}))
                      :stroke (ol.style.Stroke. #js {:color (or (:color stroke) "black")
                                                     :width (or (:width stroke) 1)})
                      ;; Default zindex asetetaan harja.views.kartta:ssa.
                      ;; Default arvo on 4 - täällä 0 ihan vaan fallbackina.
                      ;; Näin myös pitäisi huomata jos tämä ei toimikkaan.
                      :zIndex (or zindex 0)}))))


(defmethod luo-feature :polygon [{:keys [coordinates] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.Polygon. (clj->js [coordinates]))}))

(defmethod luo-feature :arrow-line [{:keys [points width scale color arrow-image arrow-image-size] :as line}]
  (assert (not (nil? points)) "Viivalla pitää olla pisteitä.")
  (let [feature (ol.Feature. #js {:geometry (ol.geom.LineString. (clj->js points))})        
        nuolet (atom [])]

    ;; Kerätään viivasegmenteille loppusijainnit ja viivan suunta
    (.forEachSegment
      (.getGeometry feature)
      (fn [start end]
        (swap! nuolet conj {:sijainti (js->clj  end)
                            :rotaatio (- (js/Math.atan2
                                          (- (second end) (second start))
                                          (- (first end) (first start))))})
        ;; forEachSegmentin ajo lopetetaan jos palautetaan tosi arvo
        false))
    (doto feature
      (.setStyle
       (clj->js
        (concat
         [(ol.style.Style. #js {:stroke (ol.style.Stroke. #js {:color (or color "black")
                                                               :width (or width 2)})
                                :zIndex 4})]
         (loop [nuolityylit []
                viimeisin-nuolen-sijainti [0 0]
                [{:keys [sijainti rotaatio]} & nuolet] @nuolet]
           (if-not sijainti
             ;; Kaikki käsitelty, palauta nuolet
             nuolityylit
             
             ;; Tee uusi nuoli, jos aiempaan on matkaa yli 3000 yksikköä
             ;; tai jos tämä on viimeinen
             (let [[x1 y1] viimeisin-nuolen-sijainti
                   [x2 y2] sijainti
                   dx (- x1 x2)
                   dy (- y1 y2)
                   dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
               
               (if (or (> dist 3000)
                       (empty? nuolet))
                 (recur (conj nuolityylit
                              (ol.style.Style.
                               #js {:geometry (ol.geom.Point. (clj->js sijainti))
                                    :image    (ol.style.Icon. #js {:src (or arrow-image 
                                                                            "images/nuoli-punainen.png")
                                                                   :opacity        1
                                                                   :scale          (or scale 2.5)
                                                                   :zIndex         6
                                                                   :rotateWithView false
                                                                   :rotation       rotaatio})}))
                        sijainti
                        nuolet)
                 (recur nuolityylit
                        viimeisin-nuolen-sijainti
                        nuolet)))))))))))


(defmethod luo-feature :tack-icon-line [{:keys [lines points img scale width zindex color] :as spec}]
  (let [feature (if (not (nil? lines))
                  (ol.Feature. #js {:geometry (ol.geom.MultiLineString. (clj->js (map :points lines)))})
                  (ol.Feature. #js {:geometry (ol.geom.LineString. (clj->js points))}))
        tyylit [(ol.style.Style. #js {:stroke (ol.style.Stroke. #js {:color (or color "black")
                                                                     :width (or width 2)})
                                      :zIndex (or zindex 4)})

                (ol.style.Style.
                  #js {:geometry (ol.geom.Point. (clj->js (.getLastCoordinate (.getGeometry feature))))
                       :image    (ol.style.Icon. #js {:src     (str +karttaikonipolku+ img)
                                                      :anchor  #js [0.5 1]
                                                      :opacity 1
                                                      :scale   (or scale 1)})
                       :zIndex   ((fnil + 4) zindex 1)})]] ;; Lisätään zindexiin 1, jos zindez=nil -> 4+1
    (doto feature
      (.setStyle (clj->js tyylit)))))

(defn- tee-kaksiosainen-ikoni [coordinates pohja img rotation anchor]
  (doto (ol.Feature. #js {:geometry (ol.geom.Point. (clj->js coordinates))})
    (.setStyle (clj->js [(ol.style.Style.
                           #js {:image  (ol.style.Icon.
                                          #js {:src      (str +karttaikonipolku+ pohja)
                                               :rotation (or rotation 0)
                                               :opacity  1
                                               :anchor   (if anchor
                                                           (clj->js anchor)
                                                           #js [0.5 0.5])})
                                :zIndex 4})

                         (ol.style.Style.
                           #js {:image  (ol.style.Icon.
                                          #js {:src     (str +karttaikonipolku+ img)
                                               :opacity 1
                                               :anchor  (if anchor
                                                          (clj->js anchor)
                                                          #js [0.5 0.5])})
                                :zIndex 5})]))))

(defmethod luo-feature :tack-icon [{:keys [coordinates img scale zindex]}]
  (doto (ol.Feature. #js {:geometry (ol.geom.Point. (clj->js coordinates))})
    (.setStyle (ol.style.Style.
                 #js {:image  (ol.style.Icon.
                                #js {:src     (str +karttaikonipolku+ img)
                                     :anchor  #js [0.5 1]
                                     :opacity 1
                                     :scale   (or scale 1)})
                      :zIndex (or zindex 4)}))))

(defmethod luo-feature :sticker-icon [{:keys [coordinates direction img]}]
  (tee-kaksiosainen-ikoni coordinates "sticker-sininen.png" img direction [0.5 0.5]))

(defmethod luo-feature :sticker-icon-line [{:keys [points img width zindex color direction] :as spec}]
  (let [feature (ol.Feature. #js {:geometry (ol.geom.LineString. (clj->js points))})
        tyylit [(ol.style.Style. #js {:stroke (ol.style.Stroke. #js {:color (or color "black")
                                                                     :width (or width 2)})
                                      :zIndex (or zindex 4)})

                (ol.style.Style.
                  #js {:geometry (ol.geom.Point. (clj->js (.getLastCoordinate (.getGeometry feature))))
                       :image    (ol.style.Icon.
                                   #js {:src      (yleiset/karttakuva (str +karttaikonipolku+ "sticker-sininen"))
                                        :rotation (or direction 0)
                                        :opacity  1
                                        :anchor   #js [0.5 0.5]})
                       :zIndex   4})

                (ol.style.Style.
                  #js {:geometry (ol.geom.Point. (clj->js (.getLastCoordinate (.getGeometry feature))))
                       :image  (ol.style.Icon.
                                 #js {:src     (str +karttaikonipolku+ img)
                                      :opacity 1
                                      :anchor  #js [0.5 0.5]})
                       :zIndex 5})]] ;; Lisätään zindexiin 1, jos zindez=nil -> 4+1
    (doto feature
      (.setStyle (clj->js tyylit)))))

(defmethod luo-feature :icon [{:keys [coordinates img direction anchor]}]
  (doto (ol.Feature. #js {:geometry (ol.geom.Point. (clj->js coordinates))})
    (.setStyle (ol.style.Style.
                 #js {:image  (ol.style.Icon.
                                #js {:src          img
                                     :anchor       (if anchor
                                                     (clj->js anchor)
                                                     #js [0.5 1])
                                     :opacity      1
                                     :rotation     (or direction 0)
                                     :anchorXUnits "fraction"
                                     :anchorYUnits "fraction"})
                      :zIndex 4}))))

(defmethod luo-feature :point [{:keys [coordinates radius] :as point}]
  #_(ol.Feature. #js {:geometry (ol.geom.Point. (clj->js coordinates))})
  (luo-feature (assoc point
                 :type :circle
                 :radius (or radius 10))))

(defmethod luo-feature :circle [{:keys [coordinates radius]}]
  (ol.Feature. #js {:geometry (ol.geom.Circle. (clj->js coordinates) radius)}))


(defmethod luo-feature :multipolygon [{:keys [polygons] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.Polygon. (clj->js (mapv :coordinates polygons)))}))

(defmethod luo-feature :multiline [{:keys [lines] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.MultiLineString. (clj->js (mapv :points lines)))}))


(defmethod luo-feature :line [{:keys [points] :as spec}]
  (ol.Feature. #js {:geometry (ol.geom.LineString. (clj->js points))}))


(defn update-ol3-layer-geometries
  "Given a vector of ol3 layer and map of current geometries and a sequence of new geometries,
updates (creates/removes) the geometries in the layer to match the new items. Returns a new
vector with the updates ol3 layer and map of geometries.
If incoming layer & map vector is nil, a new ol3 layer will be created."
  [ol3 geometry-fn [geometry-layer geometries-map] items]
  (let [create? (nil? geometry-layer)
        geometry-layer (if create? (create-geometry-layer) geometry-layer)
        geometries-map (if create? {} geometries-map)
        geometries-set (into #{} (map geometria-avain) items)
        features (.getSource geometry-layer)]

    (when create?
      (.addLayer ol3 geometry-layer))
    
    ;; Remove all ol3 feature objects that are no longer in the new geometries
    (doseq [[avain feature] (seq geometries-map)
            :when (not (geometries-set avain))]
      (.removeFeature features feature))

    ;; Create new features for new geometries and update the geometries map
    (loop [new-geometries-map {}
           [item & items] items]
      (if-not item
        ;; When all items are processed, return layer and new geometries map
        [geometry-layer new-geometries-map]
        
        (let [geom (geometry-fn item)
              avain (geometria-avain item)]
          (if-not geom
            (recur new-geometries-map items)
            (recur (assoc new-geometries-map avain
                          (or (geometries-map avain)
                              (when-let [new-shape (try
                                                     (luo-feature geom)
                                                     (catch js/Error e
                                                       (log (pr-str "Problem in luo-feature, geom: " geom " avain: " avain))
                                                       nil))]
                                (aseta-feature-geometria! new-shape item)
                                (try
                                  (.addFeature features new-shape)
                                  (catch js/Error e
                                    (log (pr-str "problem in addFeature, avain: " avain "\ngeom: "  geom  "\nnew-shape: " new-shape))))

                                ;; ikoneilla on jo oma tyyli, luo-feature tekee
                                (when-not ((:type geom) #{:icon :arrow-line :tack-icon :tack-icon-line
                                                          :sticker-icon :sticker-icon-line :clickable-area})
                                  (aseta-tyylit new-shape geom))

                                new-shape)))
                   items)))))))

(defn- update-ol3-geometries [component geometries]
  "Update the ol3 layers based on the data, mutates the ol3 map object."
  (let [{:keys [ol3 geometry-layers mapspec]} (reagent/state component)
        geometry-fn (or (:geometry-fn mapspec) identity)]

    ;; Remove any layers that are no longer present
    (doseq [[key [layer _]] geometry-layers
            :when (nil? (get geometries key))]
      (log "POISTETAAN KARTTATASO " (name key) " => " layer)
      (.removeLayer ol3 layer))

    ;; For each current layer, update layer geometries
    (loop [new-geometry-layers {}
           [layer & layers] (keys geometries)]
      (if-not layer
        (do
          (log "Map layer item counts: "
               (str/join ", "
                         (map #(str (count (second (second %))) " " (name (first %))) (seq new-geometry-layers))))
          (reagent/set-state component {:geometry-layers new-geometry-layers}))
        (let [layer-geometries (get geometries layer)]
          (if (nil? layer-geometries)
            (recur new-geometry-layers layers)
            (recur (assoc new-geometry-layers
                          layer (update-ol3-layer-geometries ol3 geometry-fn
                                                             (get geometry-layers layer)
                                                             layer-geometries))
                   layers)))))))


(defn- ol3-will-receive-props [this [_ {extent :extent geometries :geometries}]]
  (let [aiempi-extent (-> this reagent/state :extent)]
    (when-not (identical? aiempi-extent extent)
      (log "NÄYTÄ ALUE " (pr-str extent))
      (.setTimeout js/window #(keskita-kartta-alueeseen! extent) animaation-odotusaika)
      (reagent/set-state this {:extent extent})))
  
  (update-ol3-geometries this geometries))

;;;;;;;;;
;; The OpenLayers 3 Reagent component.

(defn openlayers [mapspec]
  "A OpenLayers map component."
  (reagent/create-class
    {:get-initial-state      (fn [_] {:mapspec mapspec})
     :component-did-mount    ol3-did-mount
     :reagent-render         ol3-render
     :component-will-unmount ol3-will-unmount
     :component-did-update   ol3-did-update
     :component-will-receive-props ol3-will-receive-props}))



  
