(ns harja.ui.openlayers
  "OpenLayers 3 kartta."
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<! >! chan timeout] :as async]

            [harja.ui.openlayers.featuret :refer [aseta-tyylit] :as featuret]
            [harja.ui.openlayers.taso :as taso]
            [harja.ui.openlayers.geometriataso]
            [harja.ui.dom :as dom]
            [harja.ui.animaatio :as animaatio]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.geo :as geo]

            [ol]
            [ol.Map]
            [ol.Attribution]

            [ol.View]

            [ol.source.Vector]
            [ol.layer.Vector]
            [ol.layer.Layer]

            [ol.control :as ol-control]
            [ol.interaction :as ol-interaction]

            [ol.Overlay]                                    ;; popup
            [harja.virhekasittely :as vk]
            [harja.asiakas.tapahtumat :as t]
            [harja.ui.openlayers.kuvataso :as kuvataso]
            [harja.ui.ikonit :as ikonit]
            [taoensso.timbre :as log]
            [harja.ui.openlayers.tasovalinta :as tasovalinta]
            [harja.ui.openlayers.projektiot :refer [projektio suomen-extent]]
            [harja.ui.openlayers.taustakartta :as taustakartta])

  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [harja.makrot :refer [nappaa-virhe]]
                   [harja.loki :refer [mittaa-aika]]
                   [harja.ui.openlayers :refer [disable-rendering]]))

(def ^{:doc "Odotusaika millisekunteina, joka odotetaan että
 kartan animoinnit on valmistuneet." :const true}
  animaation-odotusaika 200)

(def ^{:doc "ol3 näkymän resoluutio alkutilanteessa" :const true}
  initial-resolution 1200)

(def ^{:doc "Pienin mahdollinen zoom-taso, johon käyttäjä voi zoomata ulos"
       :const true}
  min-zoom 0)
(def ^{:doc "Suurin mahdollinen zoom-taso, johon käyttäjä voi zoomata sisään"
       :const true}
  max-zoom 16)

(def oletus-zindex featuret/oletus-zindex)

(def tooltipin-aika 3000)


;; Näihin atomeihin voi asettaa oman käsittelijän kartan
;; klikkauksille ja hoveroinnille. Jos asetettu, korvautuu
;; kartan normaali toiminta.
;; Nämä ovat normaaleja cljs atomeja, eivätkä siten voi olla
;; reagent riippuvuuksia.
(defonce klik-kasittelija (cljs.core/atom []))
(defonce hover-kasittelija (cljs.core/atom []))

(defn aseta-klik-kasittelija!
  "Asettaa kartan click käsittelijän. Palauttaa funktion, jolla käsittelijä poistetaan.
  Käsittelijöitä voi olla useita samaan aikaan, jolloin vain viimeisenä lisättyä kutsutaan."
  [funktio]
  (swap! klik-kasittelija conj funktio)
  #(swap! klik-kasittelija (fn [kasittelijat]
                             (filterv (partial not= funktio) kasittelijat))))

(defn aseta-hover-kasittelija!
  "Asettaa kartan hover käsittelijän. Palauttaa funktion, jolla käsittelijä poistetaan.
  Käsittelijoitä voi olla useita samaan aikaan, jolloin vain viimeisenä lisättyä kutsutaan."
  [funktio]
  (swap! hover-kasittelija conj funktio)
  #(swap! hover-kasittelija (fn [kasittelijat]
                              (filterv (partial not= funktio) kasittelijat))))

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
  (assert (and (= 4 (count iso)) (= 4 (count pieni)))
          "Alueen tulee olla vektori [minx miny maxx maxy]")

  (ol/extent.containsExtent (clj->js iso) (clj->js pieni)))

(defn ^:export debug-keskita [x y]
  (keskita-kartta-pisteeseen! [x y]))

(defn ^:export invalidate-size []
  (.invalidateSize @the-kartta))

(defn kartan-extent []
  (let [k @the-kartta]
    (.calculateExtent (.getView k) (.getSize k))))

(defonce openlayers-kartan-leveys (atom nil))




(defn luo-kuvataso
  "Luo uuden kuvatason joka hakee serverillä renderöidyn kuvan.
Ottaa sisään vaihtelevat parametri nimet (string) ja niiden arvot.
Näkyvän alueen ja resoluution parametrit lisätään kutsuihin automaattisesti."
  [lahde selitteet & parametri-nimet-ja-arvot]
  (kuvataso/luo-kuvataso projektio suomen-extent selitteet
                         (concat ["_" (name lahde)]
                                 parametri-nimet-ja-arvot)))

(defn sama-kuvataso? [vanha uusi]
  (kuvataso/sama? vanha uusi))

(defn keskipiste
  "Laskee geometrian keskipisteen extent perusteella"
  [geometria]
  (let [[x1 y1 x2 y2] (.getExtent geometria)]
    [(+ x1 (/ (- x2 x1) 2))
     (+ y1 (/ (- y2 y1) 2))]))

(defn feature-geometria [feature]
  (.get feature "harja-geometria"))

(defn- tapahtuman-geometria
  "Hakee annetulle ol3 tapahtumalle geometrian. Palauttaa ensimmäisen löytyneen
  geometrian."
  ([this e] (tapahtuman-geometria this e true))
  ([this e lopeta-ensimmaiseen?]
   (let [geom (volatile! [])
         {:keys [ol3 geometry-layers]} (reagent/state this)]
     (.forEachFeatureAtPixel ol3 (.-pixel e)
                             (fn [feature layer]
                               (vswap! geom conj (feature-geometria feature))
                               lopeta-ensimmaiseen?)
                             ;; Funktiolle voi antaa options, jossa hitTolerance. Eli radius, miltä featureita haetaan.
                             )

     (cond
       (empty? @geom)
       nil

       lopeta-ensimmaiseen?
       (first @geom)

       :else @geom))))

(defn- laske-kartan-alue [ol3]
  (.calculateExtent (.getView ol3) (.getSize ol3)))

(defn- tapahtuman-kuvaus
  "Tapahtuman kuvaus ulkoisille käsittelijöille"
  [this e]
  (let [c (.-coordinate e)
        tyyppi (.-type e)]
    {:tyyppi   (case tyyppi
                 "pointermove" :hover
                 "click" :click
                 "singleclick" :click
                 "dblclick" :dbl-click)
     :geometria (tapahtuman-geometria this e)
     :sijainti [(aget c 0) (aget c 1)]
     :x        (aget (.-pixel e) 0)
     :y        (aget (.-pixel e) 1)}))

(defn- aseta-postrender-kasittelija [this ol3 on-postrender]
  (.on ol3 "postrender"
       (fn [e]
         (when on-postrender
           (on-postrender e)))))

(defn- aseta-zoom-kasittelija [this ol3 on-zoom]
  (.on (.getView ol3) "change:resolution"
       (fn [e]
         (when on-zoom
           (on-zoom e (laske-kartan-alue ol3))))))

(defn- aseta-drag-kasittelija [this ol3 on-move]
  (.on ol3 "pointerdrag" (fn [e]
                           (when on-move
                             (on-move e (laske-kartan-alue ol3))))))

(defn- aseta-klik-kasittelija [this ol3 on-click on-select]
  (.on ol3 "singleclick"
       (fn [e]
         (if-let [kasittelija (peek @klik-kasittelija)]
           (kasittelija (tapahtuman-kuvaus this e))

           (if-let [g (tapahtuman-geometria this e false)]
             (when on-select (on-select g e))
             (when on-click (on-click e)))))))

;; dblclick on-clickille ei vielä tarvetta - zoomaus tulee muualta.
(defn- aseta-dblclick-kasittelija [this ol3 on-click on-select]
  (.on ol3 "dblclick"
       (fn [e]
         (if-let [kasittelija (peek @klik-kasittelija)]
           (kasittelija (tapahtuman-kuvaus this e))
           (when on-select
             (when-let [g (tapahtuman-geometria this e false)]
               (on-select g e)))))))


(defn aseta-hover-kasittelija [this ol3]
  (.on ol3 "pointermove"
       (fn [e]
         (if-let [kasittelija (peek @hover-kasittelija)]
           (kasittelija (tapahtuman-kuvaus this e))

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
  "Älä käytä tätä suoraan, vaan kutsu poista-popup! tai
  poista-popup-ilman-eventtia!"
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
  "Poistaa kartan popupin, jos sellainen on, eikä julkaise popup-suljettu
  eventtiä."
  [this]
  (poista-openlayers-popup! this))

(defn luo-overlay [koordinaatti sisalto]
  (let [elt (js/document.createElement "span")
        comp (reagent/render sisalto elt)]
    (ol.Overlay. (clj->js {:element   elt
                           :position  koordinaatti
                           :stopEvent false}))))


(defn- nayta-popup!
  "Näyttää annetun popup sisällön annetussa koordinaatissa.
  Mahdollinen edellinen popup poistetaan."
  [this koordinaatti sisalto]
  (let [{:keys [ol3 popup]} (reagent/state this)]
    (when popup
      (.removeOverlay ol3 popup))
    (let [popup (luo-overlay
                 koordinaatti
                 [:div.ol-popup
                  [:a.ol-popup-closer.klikattava
                   {:on-click #(do (.stopPropagation %)
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



(defn- ol3-did-mount [this]
  "Initialize OpenLayers map for a newly mounted map component."
  (let [{layers :layers :as mapspec} (:mapspec (reagent/state this))
        interaktiot (let [oletukset (ol-interaction/defaults
                                     #js {:mouseWheelZoom true
                                          :dragPan        false})]
                      ;; ei kinetic-ominaisuutta!
                      (.push oletukset (ol-interaction/DragPan. #js {}))
                      oletukset)
        kontrollit (ol-control/defaults #js {})

        map-optiot (clj->js {:layers       (mapv taustakartta/luo-taustakartta layers)
                             :target       (:id mapspec)
                             :controls     kontrollit
                             :interactions interaktiot})
        ol3 (ol/Map. map-optiot)

        _ (.addControl ol3 (tasovalinta/tasovalinta ol3 layers))

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
      (animaatio/kasittele-transition-end (.getElementById js/document
                                                           (:id mapspec))
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
                                              :crosshair "crosshair"
                                              :progress "progress"
                                              "")))
                   ::tooltip
                   (let [[x y teksti] args]
                     (reagent/set-state this
                                        {:hover {:x x :y y :tooltip teksti}}))))
               (recur (alts! [komento-ch unmount-ch]))))

    (.setView
     ol3 (ol.View. #js {:center     (clj->js (geo/extent-keskipiste extent))
                        :resolution initial-resolution
                        :maxZoom    max-zoom
                        :minZoom    min-zoom
                        :projection projektio}))

    ;;(.log js/console "L.map = " ol3)
    (reagent/set-state this {:ol3             ol3
                             :geometry-layers {} ; key => vector layer
                             :hover           nil
                             :unmount-ch      unmount-ch})

    ;; If mapspec defines callbacks, bind them to ol3
    (aseta-klik-kasittelija this ol3
                            (:on-click mapspec)
                            (:on-select mapspec))
    (aseta-dblclick-kasittelija this ol3
                                (:on-dblclick mapspec)
                                (:on-dblclick-select mapspec))
    (aseta-hover-kasittelija this ol3)
    (aseta-drag-kasittelija this ol3 (:on-drag mapspec))
    (aseta-zoom-kasittelija this ol3 (:on-zoom mapspec))
    (aseta-postrender-kasittelija this ol3 (:on-postrender mapspec))

    (update-ol3-geometries this (:geometries mapspec))

    (when-let [mount (:on-mount mapspec)]
      (mount (laske-kartan-alue ol3)))

    (tapahtumat/julkaise! {:aihe :kartta-nakyy})))

(defn ol3-will-unmount [this]
  (let [{:keys [ol3 geometries-map unmount-ch]} (reagent/state this)]
    (async/close! unmount-ch)))

(defn- ol3-did-update [this _]
  (let [uusi-leveys (.-offsetWidth
                     (aget (.-childNodes (reagent/dom-node this)) 0))]
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
     (when-let [piirra-tooltip? (:tooltip-fn mapspec)]
       (when-let [hover (-> c reagent/state :hover)]
         (go (<! (timeout tooltipin-aika))
             (when (= hover (:hover (reagent/state c)))
               (reagent/set-state c {:hover nil})))
         (when-let [tooltipin-sisalto
                    (or (piirra-tooltip? hover)
                        (some-> (:tooltip hover) (constantly)))]
           [:div.kartta-tooltip
            {:style {:left (+ 20 (:x hover)) :top (+ 10 (:y hover))}}
            (tooltipin-sisalto)])))]))

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
                         (map #(str (let [l (second (second %))]
                                      (if (counted? l)
                                        (count l)
                                        "N/A"))
                                    " " (name (first %)))
                              (seq new-geometry-layers))))
          (reagent/set-state component {:geometry-layers new-geometry-layers
                                        :geometries geometries}))
        (if-let [taso (get geometries layer)]
          (recur (assoc new-geometry-layers
                        layer (apply taso/paivita
                                     taso ol3
                                     (get geometry-layers layer)))
                 layers)
          (recur new-geometry-layers layers))))))

(defn- ol3-will-receive-props [this [_ {extent :extent geometries :geometries
                                        extent-key :extent-key}]]
  (let [{aiempi-extent :extent aiempi-extent-key :extent-key}
        (reagent/state this)]
    (reagent/set-state this {:extent-key extent-key
                             :extent     extent})
    (when (or (not (identical? aiempi-extent extent))
              (not= aiempi-extent-key extent-key))
      (.setTimeout js/window #(keskita-kartta-alueeseen! extent)
                   animaation-odotusaika)))

  (update-ol3-geometries this geometries))

;;;;;;;;;
;; The OpenLayers 3 Reagent component.

(defn openlayers [mapspec]
  "A OpenLayers map component."
  (reagent/create-class
    {:get-initial-state            (fn [_] {:mapspec mapspec})
     :component-did-mount          ol3-did-mount
     :reagent-render               ol3-render
     :component-will-unmount       ol3-will-unmount
     :component-did-update         ol3-did-update
     :UNSAFE_component-will-receive-props ol3-will-receive-props}))
