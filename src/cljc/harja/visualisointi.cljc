(ns harja.visualisointi
  "Selain- ja palvelinpuolen yhteisiä piirtoapureita, targetit front ja PDF. Käytetään ainakin raportoinnissa."
  (:require
    #?@(:cljs
        [[reagent.core :refer [atom] :as r]
         [cljs-time.core :as t]
         [cljs-time.coerce :as tc]])
    [clojure.string :as st]
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
        n-of-items-in-legend-row 4
        n-of-legend-rows #?(:clj (-> legend count (/ n-of-items-in-legend-row) Math/ceil)
                            :cljs (-> legend count (/ n-of-items-in-legend-row) js/Math.ceil))
        ;; we need env specific parameters for each rendering target
        #?@(:cljs [bars-top-y 25
                   spacer-y 10
                   label-area-height 10
                   legendbox-thickness 15
                   legend-label-x 20
                   legend-label-y-pos 11
                   legend-area-height (* (+ spacer-y legendbox-thickness) n-of-legend-rows)]

            :clj  [bars-top-y 5
                   spacer-y 4
                   label-area-height 3
                   legend-area-height 12
                   legendbox-thickness 4
                   legend-label-x 5
                   legend-label-y-pos 3.5])

        bar-area-height (- height bars-top-y label-area-height legend-area-height (* spacer-y 2))
        bars-bottom-y (+ bars-top-y bar-area-height)
        label-top-y (+ bars-bottom-y spacer-y)
        label-bottom-y (+ label-top-y label-area-height)
        legend-top-y (+ label-bottom-y spacer-y)
        my-half (/ my 2)
        bar-width (/ (- width mx) (count data))
        multiple-series? (-> data first value-fn vector?)
        _ (assert (or (and multiple-series? legend)
                      (and (not multiple-series?) (nil? legend)))
                  "Legend must be supplied iff data has multiple series")

        is-legend-set? (set? legend)
        value-seq (if multiple-series?
                    (keep identity (mapcat value-fn data))
                    (map value-fn data))
        is-value-map? (map? (first value-seq))
        max-value (reduce max (if is-value-map?
                                (map :value value-seq)
                                value-seq))
        min-value (reduce min (if is-value-map?
                                (map :value value-seq)
                                value-seq))
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
                                               :on-mouse-over #(do (log/debug (str category)))}]
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
       (let [legend-rows (partition-all n-of-items-in-legend-row legend)]
         (map (fn [num]
                  (let [legend-row (nth legend-rows num)]
                     (map-indexed (fn [i leg]
                                    (let [rect-x-val (+ mx (* i (/ width (count legend-row))))
                                          rect-y-val (* num (+ legendbox-thickness spacer-y))]
                                      ^{:key i}
                                      [:g
                                       [:rect {:x      rect-x-val
                                               :y      (+ legend-top-y rect-y-val)
                                               :height legendbox-thickness
                                               :width  legendbox-thickness
                                               :fill   (if (map? colors)
                                                          ((keyword leg) colors)
                                                          (nth colors i))
                                               :on-click #(on-legend-click (keyword leg))}]
                                       [:text {:x         (+ legend-label-x rect-x-val)
                                               :y         (+ legend-top-y legend-label-y-pos rect-y-val)
                                               :font-size value-font-size}

                                        leg]]))
                                  legend-row)))
              (range n-of-legend-rows))))]))

(defn round-to-decimal
  [n decimals]
  (let [a (reduce * (repeat decimals 10))]
    (-> n (* a) (Math/round) (/ a))))

(defn draw-axis
  [{:keys [x-min x-max y-min y-max title x-label y-label x-tick y-tick
           x-tick-value y-tick-value y-label-orientation width height]}
   {:keys [origin-x origin-y axis-width axis-height]}
   {:keys [x-txt-padding x-axis-font-size y-txt-padding y-axis-font-size]}]
  (let [x-tick (cond
                  x-tick x-tick
                  (and (< (count x-tick-value) 10)
                       (not (nil? x-tick-value))) (count x-tick-value)
                  :else 10)
        y-tick (cond
                  y-tick y-tick
                  (and (< (count y-tick-value) 10)
                       (not (nil? y-tick-value))) (count y-tick-value)
                  :else 10)
        x-tick (dec x-tick)
        y-tick (dec y-tick)
        x-tick-values (or x-tick-value (mapv #(-> x-max (/ x-tick) (* %) (round-to-decimal 2))
                                             (range (inc x-tick))))
        y-tick-values (or y-tick-value (mapv #(-> y-max (/ y-tick) (* %) (round-to-decimal 2))
                                             (range (inc y-tick))))
        x-tick-all (if x-tick-value
                    (dec (count x-tick-value)) x-tick)]
    ;määrritellään ne muuttujat svg:ssä, jotka on pakko
    (seq
      [
       ^{:key "axis-def"}
       [:defs
        [:marker {:id "markerArrow" :markerWidth 20 :markerHeight 15
                  :refX 0 :refY 10  :orient "auto"}
          [:path {:d "M0,10 L0,15 L20,10 L0,5 L0,10"}]]]
       ^{:key "diagram-axis"}
       [:g {:stroke "black" :stroke-width 0.5}
        ;x-axis
        [:line {:x1 origin-x :y1 origin-y
                :x2 (+ axis-width origin-x) :y2 origin-y :markerEnd "url(#markerArrow)"}]
        ;y-axis
        [:line {:x1 origin-x :y1 origin-y
                :x2 origin-x :y2 (- origin-y axis-height) :markerEnd "url(#markerArrow)"}]
        ;x-ticks
        (mapcat #(let [x-tick-space (/ axis-width x-tick-all)
                       space-timer (Math/round (* % (/ x-tick-all x-tick)))
                       x-position (-> x-tick-space (* space-timer) (+ origin-x))
                       x-tick-value (get x-tick-values space-timer)]
                  [^{:key (str % "-tick")}
                   [:line {:x1 x-position :y1 origin-y
                           :x2 x-position :y2 (- origin-y 4)}]
                   ^{:key (str % "-txt")}
                   [:text {:x x-position :y (+ origin-y x-txt-padding x-axis-font-size)
                           :text-anchor "middle" :font-size (str x-axis-font-size)}
                     x-tick-value]])
                (range (inc x-tick)))
        ;y-ticks
        (mapcat #(let [y-tick-space (/ axis-height y-tick)
                       y-position (->> y-tick-space (* %) (- axis-height) (+ 20))
                       y-tick-value (get y-tick-values %)]
                  [^{:key (str % "-tick")}
                   [:line {:x1 origin-x :y1 y-position
                           :x2 (+ origin-x 4) :y2 y-position}]
                   ^{:key (str % "-txt")}
                   [:text {:x (- origin-x y-txt-padding y-axis-font-size) :y y-position
                           :text-anchor "middle" :font-size (str y-axis-font-size)}
                     y-tick-value]])
                (range (inc y-tick)))]])))

(defn vec-of-maps?
  [vec]
  (if (and (vector? vec) (not (empty? vec)))
    (not (some #(not (map? %)) vec))
    false))

(defn apply-vec-maps
  [f key-of-interest & x]
  (let [fp (if-let [args (butlast x)]
             (apply partial f args)
             f)]
    (apply fp (map #(apply fp (key-of-interest %)) (last x)))))

(defn axis-points
  [x y x-ratio y-ratio origin-x origin-y]
  (st/trim
    (apply str (map #(str (+ origin-x (* x-ratio %1)) ","
                          (- origin-y (* y-ratio %2)) " ")
                    x y))))

(defn style-map
  [{:keys [stroke stroke-width]}]
  [:style {:stroke stroke
           :stroke-width stroke-width}])

(defn vector-disj
  [vector index]
  (vec
    (concat (subvec vector 0 index)
            (subvec vector (inc index)))))

(defn sizes
  [{:keys [x-axis-font-size y-axis-font-size legend-font-size marker-width
           x-txt-padding y-txt-padding x-label-space y-label-space
           legendbox-thickness legendbox-padding legend-label-x legend-label-y
           max-legend-height n-of-legend-columns]}]
  (let [x-axis-font-size (or x-axis-font-size 6)
        y-axis-font-size (or y-axis-font-size 6)
        legend-font-size (or legend-font-size 6)
        marker-width (or marker-width 20)
        x-txt-padding (or x-txt-padding 2)
        y-txt-padding (or y-txt-padding 2)
        x-label-space (or x-label-space 10)
        y-label-space (or y-label-space 10)
        legendbox-thickness (or legendbox-thickness 15)
        legendbox-padding (or legendbox-padding 10)
        legend-label-x (or legend-label-x 20)
        legend-label-y (or legend-label-y 11)
        max-legend-height (or max-legend-height 100)
        n-of-legend-columns (or n-of-legend-columns 1)]
    {:x-axis-font-size x-axis-font-size
     :y-axis-font-size y-axis-font-size
     :legend-font-size legend-font-size
     :marker-width marker-width
     :x-txt-padding x-txt-padding
     :y-txt-padding y-txt-padding
     :x-label-space x-label-space
     :y-label-space y-label-space
     :legendbox-thickness legendbox-thickness
     :legendbox-padding legendbox-padding
     :legend-label-x legend-label-x
     :legend-label-y legend-label-y
     :max-legend-height max-legend-height
     :n-of-legend-columns n-of-legend-columns}))

(defn diagram-points
  [{:keys [x-axis-font-size y-axis-font-size marker-width x-txt-padding y-txt-padding
           x-label-space y-label-space legendbox-thickness legendbox-padding
           max-legend-height n-of-legend-columns]}
   vals width height]
  (let [n-of-legend-rows (count (partition-all n-of-legend-columns vals))
        legend-area-true-height (* (+ legendbox-padding legendbox-thickness)
                                   n-of-legend-rows)
        legend-area-height (if (> legend-area-true-height max-legend-height)
                             max-legend-height legend-area-true-height)
        axis-width (- width y-label-space y-txt-padding y-axis-font-size marker-width)
        axis-height (- height legend-area-height x-label-space x-txt-padding x-axis-font-size marker-width)
        origin-x (+ y-label-space y-axis-font-size y-txt-padding)
        origin-y (+ axis-height marker-width)
        legend-area-top-x origin-x
        legend-area-top-y (+ origin-y x-txt-padding x-axis-font-size x-label-space)]
    {:legend-area-top-x legend-area-top-x
     :legend-area-top-y legend-area-top-y
     :legend-area-height legend-area-height
     :legend-area-true-height legend-area-true-height
     :axis-width axis-width
     :axis-height axis-height
     :origin-x origin-x
     :origin-y origin-y}))


(defn draw-legend
  [vals
   {:keys [legend-label-x legend-label-y legendbox-thickness legendbox-padding
           max-legend-height]}
   {:keys [legend-area-top-y legend-area-top-x legend-area-height
           legend-area-true-height]}
   {:keys [clicked-fn mouse-enter-fn mouse-leave-fn n-of-legend-columns scroll-bars?
           width height]}]
  (let [n-of-legend-columns (or n-of-legend-columns 1)
        column-width (/ width n-of-legend-columns)
        vals-partioned (partition-all n-of-legend-columns vals)
        legend-svg-elements (apply concat
                              (map-indexed
                                #(map-indexed (fn [column-index val]
                                                (let [rect-x-val (+ legend-area-top-x (* column-width column-index))
                                                      rect-y-val (+ legend-area-top-y
                                                                    (* (+ legendbox-thickness legendbox-padding) %1))
                                                      val-index (dec (* (inc column-index) (inc %1)))]
                                                  ^{:key (gensym "legend-")}
                                                  [:g {:on-click (clicked-fn val-index)
                                                       :on-mouse-enter (mouse-enter-fn val-index)
                                                       :on-mouse-leave (mouse-leave-fn val-index)}
                                                    [:rect {:x rect-x-val
                                                            :y rect-y-val
                                                            :height legendbox-thickness
                                                            :width  legendbox-thickness
                                                            :fill   (get-in val [:style :fill])}]
                                                    [:text {:x         (+ legend-label-x rect-x-val)
                                                            :y         (+ legend-label-y rect-y-val)
                                                            :font-size 6}
                                                      (if-let [label (:label val)]
                                                        label "line")]]))
                                              %2)
                                vals-partioned))]
    (if (and scroll-bars? (= legend-area-height max-legend-height))
      [:foreignObject {:width width
                       :height legend-area-height
                       :y legend-area-top-y}
          [:div {:style {:xmlns "http://www.w3.org/1999/xhtml"
                         :overflow "scroll"
                         :width width
                         :height max-legend-height}}
            [:svg {:xmlns "http://www.w3.org/2000/svg"
                   :width width
                   :height legend-area-true-height
                   :view-box (str 0 " " legend-area-top-y " " width " " max-legend-height)}
              legend-svg-elements]]]
      legend-svg-elements)))

(defn draw-axis-and-legend
  [vals clicked-fn mouse-enter-fn mouse-leave-fn
   {:keys [x-min x-max y-min y-max title x-label y-label x-tick y-tick
           x-tick-value y-tick-value clicked hovered width height
           max-legend-height n-of-legend-columns scroll-bars?]}]
  (let [x-max (or x-max (apply-vec-maps max :x @vals))
        y-max (or y-max (apply-vec-maps max :y @vals))
        x-min (or x-min (apply-vec-maps min :x @vals))
        y-min (or y-min (apply-vec-maps min :y @vals))
        sizes (sizes {})
        diagram-points (diagram-points sizes @vals width height)
        axis-params {:x-min x-min :x-max x-max :y-min y-min :y-max y-max :title title
                     :x-label x-label :y-label y-label :x-tick x-tick :y-tick y-tick
                     :x-tick-value x-tick-value :y-tick-value y-tick-value
                     :width width :height height}
        legend-params {:clicked-fn clicked-fn :mouse-enter-fn mouse-enter-fn
                       :mouse-leave-fn mouse-leave-fn :n-of-legend-columns n-of-legend-columns
                       :scroll-bars? scroll-bars? :width width :height height}]
    [:g
      (draw-axis axis-params diagram-points sizes)
      (draw-legend @vals sizes diagram-points legend-params)]))

(defn index-of
 [vektori elementti]
 #?(:clj (.indexOf vektori elementti)
    :cljs (.indexOf (to-array vektori) elementti)))

(defn lisaa-suorille-nollat
  [vals x-vals]
  (map (fn [mappi]
        (let [eka-indeksi (->> mappi :x first (index-of x-vals))
              toka-indeksi (->> mappi :x last (index-of x-vals))
              kaikki-x (subvec x-vals eka-indeksi (inc toka-indeksi))]
          (assoc mappi :x (vec kaikki-x)
                       :y (vec (loop [[paa & hanta] kaikki-x
                                      [x-paa & x-hanta] (:x mappi)
                                      [y-paa & y-hanta] (:y mappi)
                                      tulos []]
                                  (if (nil? paa)
                                    tulos
                                    (let [loytyi? (= x-paa paa)]
                                      (recur hanta
                                             (if loytyi? x-hanta (conj x-hanta x-paa))
                                             (if loytyi? y-hanta (conj y-hanta y-paa))
                                             (if loytyi? (conj tulos y-paa) (conj tulos 0))))))))))
      vals))

(defmulti plot
  (fn [{plot-type :plot-type}]
    (identity plot-type)))

(defmethod plot :line-plot
  [{:keys [values width height clicked hovered x-max y-max] :as plot-params}]
  (let [vals (if (vec-of-maps? values) values [values])
        x-number? (-> vals first :x first number?)
        y-number? (-> vals first :y first number?)
        x-vals (vec (sort (into #{} (mapcat :x values))))
        x-tick-value (or (:x-tick-value plot-params)
                         (if x-number?
                           nil x-vals))
        vals (lisaa-suorille-nollat vals x-vals)
        vals (atom (mapv #(let [x-vec (:x %)
                                y-vec (:y %)]
                            (cond-> %
                              (not x-number?) (assoc :x (range (count x-vec)))
                              (not y-number?) (assoc :y (range (count y-vec)))))
                         vals))]
    (fn [{:keys [values width height clicked hovered x-max y-max] :as plot-params}]
      [:svg {#?@(:clj [:xmlns  "http://www.w3.org/2000/svg"]) :width width :height height}
        (let [{:keys [axis-width axis-height origin-x origin-y]} (diagram-points (sizes {}) @vals width height)
              x-max (or x-max (apply-vec-maps max :x @vals))
              y-max (or y-max (apply-vec-maps max :y @vals))
              x-ratios (map #(/ axis-width x-max) @vals)
              y-ratios (map #(/ axis-height y-max) @vals)
              points (mapv #(axis-points (:x %1) (:y %1) %2 %3 origin-x origin-y)
                           @vals x-ratios y-ratios)
              _ (println (pr-str points))
              action-fn (fn [style-map index add?]
                          (if (= style-map :delete)
                            (reset! vals (vector-disj @vals index))
                            (if add?
                              (swap! vals update-in [index :style] #(merge % style-map))
                              (swap! vals update-in [index :style] #(apply dissoc % (keys style-map))))))
              clicked-fn (fn [index]
                          (fn [event] (action-fn clicked index false)))
              mouse-enter-fn (fn [index]
                              (fn [event] (action-fn hovered index true)))
              mouse-leave-fn (fn [index]
                              (fn [event] (action-fn hovered index false)))]

          [:g
            (draw-axis-and-legend vals clicked-fn mouse-enter-fn mouse-leave-fn (assoc plot-params :x-tick-value x-tick-value))
            ;draw lines
            (doall
              (map-indexed #(identity
                              ^{:key (gensym "plot-line-")}
                              [:g.klikattava
                                  {:stroke "black" :stroke-width 1
                                   :pointer-events "painted"
                                   :on-click (clicked-fn %1)
                                   :on-mouse-enter (mouse-enter-fn %1)
                                   :on-mouse-leave (mouse-leave-fn %1)}
                                (if (= 1 (count (:x %2)))
                                  (do (str (re-find #"(.+)," (get points %1)) " " (re-find #",(.+)" (get points %1)))
                                      [:circle {:cx (second (re-find #"(.+)," (get points %1)))
                                                :cy (second (re-find #",(.+)" (get points %1)))
                                                :r 2}])
                                  [:polyline (merge {:points (get points %1)
                                                     :fill "none"}
                                                    (style-map (:style %2)))])])
                           @vals))])])))
