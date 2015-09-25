(ns harja.ui.visualisointi
    "Data visualization components."
    (:require [reagent.core :refer [atom] :as r]
              [cljs-time.core :as t]
              [cljs-time.coerce :as tc]
              [harja.loki :refer [log]]
              [harja.ui.komponentti :as komp]
              [harja.pvm :as pvm]))

(defn polar->cartesian [cx cy radius angle-deg]
    (let [rad (/ (* js/Math.PI (- angle-deg 90)) 180.0)]
        [(+ cx (* radius (js/Math.cos rad)))
         (+ cy (* radius (js/Math.sin rad)))]))


(defn arc [cx cy r db de color width]
    (let [[ax ay] (polar->cartesian cx cy r db)
          [lx ly] (polar->cartesian cx cy r de)
          large? (if (< (- de db) 180) 0 1)
          dir? 1]
        [:path {:d (str "M " ax " " ay " "
                        "A" r " " r " 0 " large? " " dir? " " lx " " ly)
                :fill "none"
                :stroke color
                :stroke-width width}]))

(defn arc-text [cx cy r ang text]
    (let [[x y] (polar->cartesian cx cy r ang)]
        [:text {:x x :y y :text-anchor "middle"
                :transform (str "rotate(" ang " " x "," y ")")} text]))

(def +colors+ ["#468966" "#FFF0A5" "#FFB03B" "#B64926" "#8E2800"])

(defn pie [{:keys [width height radius show-text show-legend]} items]
  (let [hover (atom nil)
        tooltip (atom nil)] ;; tooltip text
    (fn [{:keys [width height radius show-text show-legend]} items]
      (let [cx (/ width 2)
            cy (+ radius 5) ;;(/ height 2)
            radius (or radius (- (/ width 2) 6))
            total (reduce + 0 (vals items))
            hover! (fn [label]
                     (reset! hover label)
                     (when-let [count (get items label)]
                       (reset! tooltip
                               (str label ": " (.toFixed (* 100 (/ count total)) 1)
                                    "% (" count ")"))))
                
            hovered @hover
            all-items (seq items)]
        (loop [slices (list)
               angle 0
               [[label count] & items] all-items
               colors (cycle +colors+)]
          (if-not label
            [:span.pie
             (when show-legend
               [:div.pie-legend
                (map (fn [l c]
                       ^{:key l}
                       [:div.pie-legend-item.klikattava {:on-click #(hover! l)}
                        [:div.pie-legend-color {:style {:background-color c}}]
                        l])
                     (map first all-items)
                     (cycle +colors+))])
             [:svg {:width width
                    :height height}
              slices
              (when-let [tip @tooltip]
                [:text {:x (/ width 2) :y (- height 10) :text-anchor "middle"}
                 tip])]]
                  
            (let [slice-angle (* 360 (/ count total))
                  start-angle angle
                  end-angle (+ angle slice-angle)
                  large? (if (< (- end-angle start-angle) 180) 0 1)
                  [sx sy] (polar->cartesian cx cy radius start-angle)
                  [ex ey] (polar->cartesian cx cy radius end-angle)
                  [tx ty] (polar->cartesian cx cy (* 0.55 radius) (+ start-angle (/ slice-angle 2)))]
              (recur (conj slices
                           ^{:key label}
                           [:g.klikattava
                            {:on-click #(hover! label)
                             :on-mouse-over #(hover! label)
                             :on-mouse-out #(do (reset! hover nil)
                                                (reset! tooltip nil))}
                            (if (= 360 slice-angle)
                              [:circle {:cx cx :cy cy :r radius
                                        :fill (first colors)
                                        :stroke "black"
                                        :stroke-width (if (= hovered label) 3 1)}]
                              [:path {:d (str "M" cx " " cy " "
                                              "L" sx " " sy " "
                                              "A" radius " " radius " 0 " large? " 1 " ex " " ey
                                              "L" cx " " cy)

                                      :fill (first colors)
                                      :stroke "black"
                                      :stroke-width (if (= hovered label) 3 1)

                                      }])
                            (cond
                             (= show-text :percent)
                             [:text {:x tx :y ty :text-anchor "middle"}
                              (str (.toFixed (* 100 (/ count total)) 1) "%")]
                                                   

                             show-text
                             [:text {:x tx :y ty :text-anchor "middle"
                                     :transform (str "rotate( " (let [a (+ start-angle (/ slice-angle 2))]
                                                                  (if (and (> a 90) (< a 270))
                                                                    (- a 180) a))
                                                     " " tx "," ty ")")}
                              label]

                             :default nil)])
                     (+ angle slice-angle)
                     items
                     (rest colors)))))))))


(defn bars [_ data]
  (let [hover (atom nil)]
    (fn [{:keys [width height label-fn value-fn key-fn color-fn color ticks]}  data]
      (let [label-fn (or label-fn first)
            value-fn (or value-fn second)
            key-fn (or key-fn hash)
            color-fn (or color-fn (constantly (or color "blue")))
            mx 20 ;; margin-x
            my 40 ;; margin-y
            hmy (/ my 2)
            bar-width (/ (- width mx) (count data))
            hovered @hover
            max-value (reduce max (map value-fn data))
            min-value (reduce min (map value-fn data))
            value-range (- max-value min-value)
            value-height #(/ (* (- height my) %) max-value)
            ]
        (log "Value range " min-value " -- " max-value " == " value-range)
        [:svg {:width width :height height}
         (map-indexed (fn [i d]
                        (let [label (label-fn d)
                              value (value-fn d)
                              bar-height (value-height value)
                              x (+ mx (* bar-width i))] ;; FIXME: scale min-max
                          ^{:key (key-fn d)}
                          [:g {:on-mouse-over #(reset! hover d)
                               :on-mouse-out #(reset! hover nil)}
                           [:rect {:x x
                                   :y (- height bar-height hmy)
                                   :width (* bar-width 0.75)
                                   :height bar-height
                                   :fill (color-fn d)}]
                                        ;(when (= hovered d)
                           [:text {:x (+ x (/ bar-width 2)) :y (- height bar-height hmy 2)
                                   :text-anchor "end"}
                            (.toFixed value 2)]
                           [:text {:x (+ x (/ bar-width 2)) :y height
                                   :text-anchor "end"}
                            label]]))
                      data)

         ]))))

(defn timeline [opts times]
  (let [component (r/current-component)
        svg->screen (fn [[x y]]
                      (let [svg (r/dom-node component)
                            point (.createSVGPoint svg)
                            matrix (.getScreenCTM svg)]
                        (set! (.-x point) x)
                        (set! (.-y point) y)
                        (let [p (.matrixTransform point matrix)]
                          [(.-x p) (.-y p)])))
        screen->svg (fn [[x y]]
                      (let [svg (r/dom-node component)
                            point (.createSVGPoint svg)
                            matrix (.inverse (.getScreenCTM svg))]
                        (set! (.-x point) x)
                        (set! (.-y point) y)
                        (let [p (.matrixTransform point matrix)]
                          [(.-x p) (.-y p)])))
                      
        evt->pos (fn [event]
                   (screen->svg [(.-clientX event) (.-clientY event)]))]
    (komp/luo
     {:component-did-mount (fn [this]
                             (let [dom-node (r/dom-node this)]
                               (log "SVG node: " dom-node)
                               (log "point: " (.createSVGPoint dom-node))
                               (log "screen ctm: " (.getScreenCTM dom-node))))}
   
     (fn [{:keys [width height range slice hover on-hover on-click] :as opts} times]
       (let [[start end] range
             margin-x 20
             margin-y 10
             day-range (t/in-days (t/interval start end))
             x-scale (/ (- width margin-x margin-x) day-range)
             day-width (* 0.8 x-scale)
             day-height 20 ;; (- height margin-y margin-y)
             empty-height (/ day-height 4)
             empty-y (- day-height  (/ empty-height 2))
             text-y (+ 5 margin-y margin-y day-height)
             date-to-x (fn [date]
                         (+ margin-x 
                            (* x-scale (t/in-days (t/interval start date)))))
             next-day #(t/plus % (t/days 1))]
         [:svg.timeline {:width width :height height
                         :on-click (fn [e]
                                     (let [[x _] (evt->pos e)
                                           day (/ (- x margin-x) x-scale)]
                                       (log "X: " x ", DAY: " day)
                                       (when on-click
                                         (on-click (t/plus start (t/days (int day)))))))}
          [:g.timeline-dates 
           (loop [acc (list)
                  now start
                  empty-day nil]
             (if (t/after? now end)
               ;; Aikajana loppui, palautetaan lapset
               (doall (map-indexed (fn [i children] ^{:key i} [:g children])
                                   (partition 50 1 []
                                              (if empty-day
                                                (conj acc empty-day)
                                                acc))))

               
               (let [active? (times now) 
                     x (date-to-x now)]
                 (if-not active?
                   ;; Niputetaan monta ei-aktiivista päivä
                   (if empty-day
                     ;; Jo olemassa oleva ei-aktiivisten päivien juoksu menossa, yhdistetään siihen
                     (recur acc (next-day now) (assoc-in empty-day [1 :x2] (+ x day-width)))

                     ;; Ei ole ei-aktiivisten päivien juoksua menossa, luodaan sellainen
                     (recur acc (next-day now) [:line.ei-aktiiviset-paivat
                                                {:x1 x :x2 (+ x day-width) :y1 empty-y :y2 empty-y
                                                 :style {:stroke-width 3}}]))

                   ;; Tämä päivä on aktiivinen, piirretään rect
                   (let [acc (conj acc
                                   (with-meta
                                     [:rect {:x x :y margin-y
                                             :width day-width :height day-height
                                             :style {:fill "blue"}}]
                                     
                                     {:key (tc/to-long now)}))]
                     (recur (if empty-day
                              (conj acc empty-day)
                              acc)
                            (next-day now) nil))))))]

          [:g.timeline-months
           (loop [acc (list)
                  now start]
             (if (t/after? now end)
               acc
               (if (= (t/day now) 1)
                 (recur (conj acc
                              (let [x (date-to-x now)]
                                ^{:key (tc/to-long now)}
                                [:g
                                 [:line {:x1 x :y1 (+ 5 day-height) :x2 x :y2 text-y
                                         :style {:stroke "black"}}]
                                 [:text {:x (+ 2 x) :y text-y}
                                  (pvm/kuukauden-lyhyt-nimi (t/month now))]]))
                        (next-day now))
                 (recur acc (next-day now)))))]
          
          (when slice
            (let [[slice-start slice-end] slice
                  start-x (date-to-x slice-start)
                  end-x (date-to-x slice-end)]
              [:rect.livi-valittu {:x start-x :y (- margin-y 5)
                                   :width (- end-x start-x)
                                   :height (+ day-height 10)}]))
          (comment (when hover
                     (let [[hover-start hover-end] hover
                           start-x (date-to-x hover-start)
                           end-x (date-to-x hover-end)]
                       [:rect {:x start-x :y (- margin-y 5)
                               :width (- end-x start-x)
                               :height (+ day-height 10)
                               :style {:fill "blue" :fill-opacity 0.4}}])))
          ])))))
     
