(ns harja.visualisointi
  "Selain- ja palvelinpuolen yhteisiä piirtoapureita, targetit front ja PDF. Käytetään ainakin raportoinnissa."
  (:require
    #?@(:cljs
        [[reagent.core :refer [atom] :as r]
         [cljs-time.core :as t]
         [cljs-time.coerce :as tc]])

    [harja.pvm :as pvm]
    [taoensso.timbre :as log]))

(def pi #?(:cljs js/Math.PI :clj Math/PI))
(defn cos
  [x]
  #?(:cljs (js/Math.cos x) :clj (Math/cos x)))
(defn sin
  [x]
  #?(:cljs (js/Math.sin x) :clj (Math/sin x)))

(defn polar->cartesian [cx cy radius angle-deg]
  (let [rad (/ (* pi (- angle-deg 90)) 180.0)]
    [(+ cx (* radius (cos rad)))
     (+ cy (* radius (sin rad)))]))


(defn arc [cx cy r db de color width]
  (let [[ax ay] (polar->cartesian cx cy r db)
        [lx ly] (polar->cartesian cx cy r de)
        large? (if (< (- de db) 180) 0 1)
        dir? 1]
    [:path {:d            (str "M " ax " " ay " "
                               "A" r " " r " 0 " large? " " dir? " " lx " " ly)
            :fill         "none"
            :stroke       color
            :stroke-width width}]))

(defn arc-text [cx cy r ang text]
  (let [[x y] (polar->cartesian cx cy r ang)]
    [:text {:x         x :y y :text-anchor "middle"
            :transform (str "rotate(" ang " " x "," y ")")} text]))

(def +colors+ ["#468966" "#FFF0A5" "#FFB03B" "#B64926" "#8E2800"])

(defn pie [{:keys [width height radius show-text show-legend]} items]
  (let [hover (atom nil)
        tooltip (atom nil)]                                 ;; tooltip text
    (fn [{:keys [width height radius show-text show-legend]} items]
      (let [cx (/ width 2)
            cy (+ radius 5)                                 ;;(/ height 2)
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
             [:svg {#?@(:clj [:xmlns  "http://www.w3.org/2000/svg"])
                    :width  width
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
                            {:on-click      #(hover! label)
                             :on-mouse-over #(hover! label)
                             :on-mouse-out  #(do (reset! hover nil)
                                                 (reset! tooltip nil))}
                            (if (= 360 slice-angle)
                              [:circle {:cx           cx :cy cy :r radius
                                        :fill         (first colors)
                                        :stroke       "black"
                                        :stroke-width (if (= hovered label) 3 1)}]
                              [:path {:d            (str "M" cx " " cy " "
                                                         "L" sx " " sy " "
                                                         "A" radius " " radius " 0 " large? " 1 " ex " " ey
                                                         "L" cx " " cy)

                                      :fill         (first colors)
                                      :stroke       "black"
                                      :stroke-width (if (= hovered label) 3 1)}])


                            (cond
                              (= show-text :percent)
                              [:text {:x tx :y ty :text-anchor "middle"}
                               (str (.toFixed (* 100 (/ count total)) 1) "%")]


                              show-text
                              [:text {:x         tx :y ty :text-anchor "middle"
                                      :transform (str "rotate( " (let [a (+ start-angle (/ slice-angle 2))]
                                                                   (if (and (> a 90) (< a 270))
                                                                     (- a 180) a))
                                                      " " tx "," ty ")")}
                               label]

                              :default nil)])
                     (+ angle slice-angle)
                     items
                     (rest colors)))))))))


(defn bars [{:keys [width height label-fn value-fn key-fn color-fn color colors
                    ticks format-amount hide-value? margin-x margin-y bar-padding
                    value-font-size tick-font-size font-size y-axis-font-size
                    legend on-legend-click]} data]
  (let [label-fn (or label-fn first)
        value-fn (or value-fn second)
        key-fn (or key-fn hash)
        color-fn (or color-fn (constantly (or color "blue")))
        colors (or colors ["#0066cc" "#A9D0F5" "#646464" "#afafaf" "#770000" "#ff9900"])
        mx (or margin-x 40)                                 ;; margin-x
        my (or margin-y 40)                                 ;; margin-y
        bar-padding (or bar-padding 0.9)
        value-font-size (or value-font-size "8pt")
        tick-font-size (or tick-font-size "7pt")
        y-axis-font-size (or y-axis-font-size "6pt")
        ;; we need env specific parameters for each rendering target
        #?@(:cljs [bars-top-y 25
                   spacer-y 10
                   label-area-height 10
                   legend-area-height (+ 40 (* 50 (quot (count (vec legend)) 4)))
                   legendbox-thickness 15
                   legend-label-x 20
                   legend-label-y-pos 11]

            :clj  [bars-top-y 5
                   spacer-y 4
                   label-area-height 3
                   legend-area-height 12
                   legendbox-thickness 4
                   legend-label-x 5
                   legend-label-y-pos 3.5])

        bar-area-height (- height bars-top-y label-area-height legend-area-height)
        bars-bottom-y (+ bars-top-y bar-area-height)
        label-top-y (+ bars-bottom-y spacer-y)
        label-bottom-y (+ label-top-y label-area-height)
        legend-top-y (+ label-bottom-y spacer-y)
        my-half (/ my 2)
        bar-width (/ (- width mx) (count data))
        multiple-series? (or (vector? (value-fn (first data))) (= data []))
        _ (assert (or (and multiple-series? legend)
                      (and (not multiple-series?) (nil? legend)))
                  "Legend must be supplied iff data has multiple series")

        is-legend-set? (set? legend)
        value-seq (if multiple-series?
                    (keep identity (mapcat value-fn data))
                    (map value-fn data))
        is-value-map? (map? (first value-seq))
        ;_ (js/console.log (pr-str "ms" multiple-series? "ivm" is-value-map? "vs" value-seq "asd" (first data)))
        max-value (if (empty? value-seq)
                    1
                    (reduce max (if is-value-map?
                                  (map :value value-seq)
                                  value-seq)))
        min-value (if (empty? value-seq)
                    0
                    (reduce min (if is-value-map?
                                  (map :value value-seq)
                                  value-seq)))
        value-range (- max-value min-value)
        ;leg-of-interest (atom nil)
        scale (if (= 0 max-value)
                1
                max-value)
        value-height #(/ (* bar-area-height %)
                         scale)
        format-amount (or format-amount #(.toFixed % 2))
        number-of-items (count data)
        show-every-nth-label (if (< number-of-items 13)
                               1
                               (Math/ceil (/ number-of-items 12)))
        hide-value? (or hide-value? (constantly false))]
    (log/debug "Value range " min-value " -- " max-value " == " value-range)
    [:svg {#?@(:clj [:xmlns  "http://www.w3.org/2000/svg"]) :width width :height height}
     [:g
      (for [i (range (count data))]
        (when (and multiple-series? (odd? i))
          ^{:key i}
          [:rect {:x      (+ mx (* bar-width i))
                  :y      bars-top-y
                  :width  bar-width
                  :height bar-area-height
                  :fill   "#F0F0F0"}])) ;light gray

      (map-indexed (fn [i item]
                    (with-meta item {:key (str (gensym "tick") i)}))
        (for [tick (or ticks [max-value (* 0.75 max-value) (* 0.50 max-value) (* 0.25 max-value) 0])
               :let [tick-y (- bars-bottom-y (value-height tick))]]
          [:g
           [:text {:font-size y-axis-font-size :text-anchor "end"
                   :x (- mx 3)
                   :y tick-y}
            (str tick)]
           [:line {:x1 mx :y1 tick-y :x2 width :y2 tick-y
                   #?@(:cljs [:style {:stroke           "rgb(200,200,200)"
                                      :stroke-width     0.5
                                      :stroke-dasharray "5,1"}]
                       :clj  [:style (str "stroke:rgb(200,200,200);stroke-width:0.5;")
                              :stroke-dasharray "3,1"])}]]))
      (map-indexed (fn [i d]
                     (let [label (label-fn d)
                           value (value-fn d)
                           values (if (vector? value)
                                    value
                                    [value])
                           start-x (+ mx (* bar-width i))]
                       ^{:key i}
                       [:g
                        (when (zero? (rem i show-every-nth-label))
                             [:text {:x           (+ start-x (/ (* bar-padding bar-width) 2))
                                     :y label-top-y
                                     :text-anchor "middle"
                                     :font-size   tick-font-size}
                              label])
                        (let [all-bars-width bar-width
                              bar-width-original (/ all-bars-width (if (empty? values) 1 (count values)))
                              bar-width (* 0.9 bar-width-original)
                              bar-set-margin-x (if multiple-series?
                                                 (max 1 (* 0.05 all-bars-width))
                                                 0)]
                          (when-not (empty? values)
                            (map-indexed
                              (fn [j value]
                                (let [value-num (if is-value-map?
                                                  (:value value)
                                                  value)
                                      category (when is-value-map? (:category value))]
                                  (when value
                                    (let [bar-height (value-height value-num)
                                          x (+ bar-set-margin-x
                                               (+ start-x (* bar-width j)))] ;; FIXME: scale min-max
                                      ^{:key j}
                                      [:g
                                       [:rect {:x      x
                                               :y      (- bars-bottom-y bar-height)
                                               :width  (* bar-padding bar-width)
                                               :height bar-height
                                               :fill   (if (map? colors)
                                                          ((keyword category) colors)
                                                          (nth colors j)) #_(color-fn d)
                                               :on-mouse-over #(do (println (str category)))}]
                                       (when-not (hide-value? value)
                                         [:text {:x           (+ x (/ (* bar-padding bar-width) 2))
                                                 :y (- bars-bottom-y bar-height 2)
                                                 :text-anchor "middle"
                                                 :font-size   value-font-size}
                                          (format-amount value-num)])]))))
                              values)))]))
                   data)]

     ;; show legend if any
     (when multiple-series?
       (map (fn [num]
                (let [next-5 (take 4 (drop (* 4 num) (vec legend)))]
                   (map-indexed (fn [i leg]
                                  (let [rect-x-val (+ mx (* i (/ width (count next-5))))]
                                    ^{:key i}
                                    [:g
                                     [:rect {:x      rect-x-val
                                             :y      (+ legend-top-y (* num (+ legendbox-thickness spacer-y)))
                                             :height legendbox-thickness
                                             :width  legendbox-thickness
                                             :fill   (if (map? colors)
                                                        ((keyword leg) colors)
                                                        (nth colors i))
                                             :on-click #(on-legend-click (keyword leg))}]
                                     [:text {:x         (+ legend-label-x rect-x-val)
                                             :y         (+ legend-top-y legend-label-y-pos (* num (+ legendbox-thickness spacer-y)))
                                             :font-size value-font-size}

                                      leg]]))
                                next-5)))
            (range (inc (quot (count (vec legend)) 4)))))]))
