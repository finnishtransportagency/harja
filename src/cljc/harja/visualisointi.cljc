(ns harja.visualisointi
  "Selain- ja palvelinpuolen yhteisiä piirtoapureita, targetit front ja PDF. Käytetään ainakin raportoinnissa."
  (:require
    #?@(:cljs
        [[reagent.core :refer [atom] :as r]
         [cljs-time.core :as t]
         [cljs-time.coerce :as tc]])

    [harja.pvm :as pvm]
    [harja.lokitus :refer [log]]))

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
             [:svg {:xmlns  "http://www.w3.org/2000/svg"
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
                                      :stroke-width (if (= hovered label) 3 1)

                                      }])
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


(defn bars [{:keys [width height label-fn value-fn key-fn color-fn color
                    ticks format-amount hide-value? margin-x margin-y
                    value-font-size tick-font-size font-size y-axis-font-size
                    legend]} data]
  (let [label-fn (or label-fn first)
        value-fn (or value-fn second)
        key-fn (or key-fn hash)
        color-fn (or color-fn (constantly (or color "blue")))
        colors ["blue" "red" "green" "yellow"]
        mx (or margin-x 40)                                 ;; margin-x
        my (or margin-y 40)                                 ;; margin-y
        my-half (/ my 2)
        margin-y-legend 3.5
        bar-width (/ (- width mx) (count data))
        multiple-series? (vector? (value-fn (first data)))
        _ (log "mx " mx)
        _ (log "my " my)
        _ (log "margin-y " margin-y)
        _ (log "my-half " my-half)
        _ (assert (or (and multiple-series? legend)
                      (and (not multiple-series?) (nil? legend)))
                  "Legend must be supplied iff data has multiple series")
        max-value (reduce max (if multiple-series?
                                (keep identity (mapcat value-fn data))
                                (map value-fn data)))
        min-value (reduce min (if multiple-series?
                                (keep identity (mapcat value-fn data))
                                (map value-fn data)))
        _ (log "min-value" min-value)
        _ (log "max-value" max-value)
        value-range (- max-value min-value)
        scale (if (= 0 max-value)
                1
                max-value)
        value-height #(/ (* (- height my) %)
                         scale)
        format-amount (or format-amount #(.toFixed % 2))
        number-of-items (count data)
        show-every-nth-label (if (< number-of-items 13)
                               1
                               (Math/ceil (/ number-of-items 12)))
        hide-value? (or hide-value? (constantly false))
        value-font-size (or value-font-size "8pt")
        tick-font-size (or tick-font-size "7pt")
        y-axis-font-size (or y-axis-font-size "6pt")]
    (log "Value range " min-value " -- " max-value " == " value-range)
    [:svg {:xmlns "http://www.w3.org/2000/svg" :width width :height height}
     [:g
      (for [i (range (count data))]
        (when (and multiple-series? (odd? i))
          ^{:key i}
          [:rect {:x      (+ mx (* bar-width i))
                  :y      my-half
                  :width  bar-width
                  :height (- height margin-y)
                  :fill   "#F0F0F0" ;light gray
                  }]))
      (for [tick (or ticks [max-value (* 0.75 max-value) (* 0.50 max-value) (* 0.25 max-value) 0])
            :let [tick-y (- height (value-height tick) my-half)]]
        ^{:key tick}
        [:g
         [:text {:font-size y-axis-font-size :text-anchor "end" :x (- mx 3) :y tick-y}
          (str tick)]
         [:line {:x1 mx :y1 tick-y :x2 width :y2 tick-y
                 #?@(:cljs [:style {:stroke           "rgb(200,200,200)"
                                    :stroke-width     0.5
                                    :stroke-dasharray "5,1"}]
                     :clj  [:style (str "stroke:rgb(200,200,200);stroke-width:0.5;")
                            :stroke-dasharray "3,1"])}]])
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
                             [:text {:x           (+ start-x (/ (* 0.9 bar-width) 2)) :y (- height 5)
                                     :text-anchor "middle"
                                     :font-size   tick-font-size}
                              label])
                        (let [bar-width-original (/ bar-width (if (empty? values) 1 (count values)))
                              bar-width (* 0.9 bar-width-original)
                              ;; fixme: marginaalin laskemiseen loogisesti toimiva koodi olisi kiva.
                              ;; pylväs/pylväät halutaan taustavärinsä keskelle
                              ;; bar-set-margin-x pitäisi matemaattisesti olla  (* 0.05 bar-width-original)
                              ;; mutta ainakin PDF:ssä se ei vaikuta ollenkaan!
                              bar-set-margin-x (if multiple-series?
                                                 1
                                                 0)]
                          (when-not (empty? values)
                            (map-indexed
                              (fn [j value]
                                (when value
                                  (let [bar-height (value-height value)
                                        x (+ bar-set-margin-x
                                             (+ start-x (* bar-width j)))] ;; FIXME: scale min-max
                                    ^{:key j}
                                    [:g
                                     [:rect {:x      x
                                             :y      (- height bar-height my-half)
                                             :width  (* 0.90 bar-width)
                                             :height bar-height
                                             :fill   (nth colors j) #_(color-fn d)}]
                                     (when-not (hide-value? value)
                                       [:text {:x           (+ x (/ (* 0.90 bar-width) 2)) :y (- height bar-height my-half 2)
                                               :text-anchor "middle"
                                               :font-size   value-font-size}
                                        (format-amount value)])])))
                              values)))]))
                   data)]

     ;; show legend if any
     (when multiple-series?
       (map-indexed (fn [i leg]
                      (let [rect-x-val (+ mx (* i (/ width (count legend))))
                            y-val height]
                        ^{:key i}
                        [:g
                         [:rect {:x      rect-x-val
                                 :y      y-val
                                 :height 4
                                 :width  4
                                 :fill   (nth colors i)}]
                         [:text {:x         (+ 5 rect-x-val)
                                 :y         (+ y-val margin-y-legend)
                                 :font-size value-font-size
                                 }
                          leg]]))
                    legend))]))
