(ns harja.ui.visualisointi
    "Data visualization components."
    (:require [reagent.core :refer [atom]]))

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
        (add-watch tooltip ::debug (fn [_ _ old new] (.log js/console "NEW: " (pr-str new))))
        (fn [{:keys [width height radius show-text show-legend]} items]
          (let [cx (/ width 2)
                cy (/ height 2)
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
                      [tx ty] (polar->cartesian cx cy (* 0.65 radius) (+ start-angle (/ slice-angle 2)))]
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
                  mx 40 ;; margin-x
                  my 20 ;; margin-y
                  bar-width (/ (- width mx) (count data))
                  hovered @hover
                  max-value (reduce max (map value-fn data))
                  min-value (reduce min (map value-fn data))
                  value-range (- max-value min-value)
                  scale (/ height value-range)
                  ]
                [:svg {:width width :height height}
                 (map-indexed (fn [i d]
                                  (let [label (label-fn d)
                                        value (value-fn d)
                                        bar-height (* value scale)] ;; FIXME: scale min-max
                                      ^{:key (key-fn d)}
                                      [:g {:on-mouse-over #(reset! hover d)
                                           :on-mouse-out #(reset! hover nil)}
                                       [:rect {:x (+ mx (* i bar-width))
                                               :y (- height bar-height my)
                                               :width (* bar-width 0.75)
                                               :height bar-height
                                               :fill (color-fn d)}]
                                       (when (= hovered d)
                                           [:text {:x (/ width 2) :y (- height 5)
                                                   :text-anchor "middle"}
                                            (str label ": " (.toFixed value 2))])]))
                              data)
                 ;; first and last labels
                 [:text {:x mx :y (- height 5) :text-anchor "left"}
                  (label-fn (first data))]
                 [:text {:x width :y (- height 5) :text-anchor "end"}
                  (label-fn (last data))]

                 ;; render ticks that are in the min-value - max-value range
                 (for [[label value] ticks
                       :when (< min-value value max-value)
                       :let [y (- height (* value scale) my)]]
                     ^{:key label}
                     [:g
                      [:line {:x1 mx :y1 y :x2 width :y2 y
                              :style {:stroke "black" :stroke-dasharray "5, 5"}}]
                      [:text {:x (- mx 5) :y (+ y 5) :text-anchor "end"} label]])
                 ]))))
