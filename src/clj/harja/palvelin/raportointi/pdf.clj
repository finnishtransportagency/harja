(ns harja.palvelin.raportointi.pdf
  "Raportoinnin elementtien renderöinti PDF:ksi"
  (:require [harja.tyokalut.xsl-fo :as fo]
            [clojure.string :as str]))

(def muodosta-pdf nil)
(defmulti muodosta-pdf
  "Muodostaa PDF:n XSL-FO hiccupin annetulle raporttielementille.
  Dispatch tyypin mukaan (vektorin 1. elementti)."
  (fn [elementti]
    (assert (and (vector? elementti)
                 (> (count elementti) 1)
                 (keyword? (first elementti)))
            "Raporttielementin on oltava vektori, jonka 1. elementti on tyyppi ja muut sen sisältöä.")
    (first elementti)))

(defmethod muodosta-pdf :taulukko [[_ sarakkeet data]]
  [:fo:table {:border "solid 0.1mm black"}
   (for [{:keys [otsikko leveys]} sarakkeet]
     [:fo:table-column {:column-width leveys}])
   [:fo:table-header
    [:fo:table-row
     (for [otsikko (map :otsikko sarakkeet)]
       [:fo:table-cell {:border "solid 0.1mm black" :background-color "#afafaf" :font-weight "bold" :padding "1mm"}
        [:fo:block otsikko]])]]
   [:fo:table-body
    (for [rivi data]
      [:fo:table-row
       (for [i (range (count sarakkeet))
             :let [arvo (nth rivi i)]]
         [:fo:table-cell {:border "solid 0.1mm black" :padding "1mm"}
          [:fo:block (str arvo)]])])]])


(defmethod muodosta-pdf :otsikko [[_ teksti]]
  [:fo:block {:padding-top "5mm" :font-size "16pt"} teksti])

(defmethod muodosta-pdf :teksti [[_ teksti]]
  [:fo:block {} teksti])

(defmethod muodosta-pdf :pylvaat [[_ {:keys [otsikko vari]} pylvaat]]
  ;;[:pylvaat "Otsikko" [[pylvas1 korkeus1] ... [pylvasN korkeusN]]] -> bar chart svg
  [:fo:block
   [:fo:block {:font-weight "bold"} otsikko]
   [:fo:instream-foreign-object {:content-width "15cm" :content-height "10cm"}
    [:svg {:xmlns "http://www.w3.org/2000/svg"}
     ;; FIXME
     (let [data pylvaat
           width 150
           height 80
           label-fn first
           value-fn second
           color-fn (constantly (or vari "blue"))
           ticks nil
           mx 20 ;; margin-x
           my -10 ;; margin-y
           bar-width (/ (- width mx) (count data))
           max-value (reduce max (map value-fn data))
           min-value (reduce min (map value-fn data))
           value-range (- max-value min-value)
           scale (if (zero? value-range)
                   1
                   (/ height value-range))]
       [:g
        ;; render ticks that are in the min-value - max-value range
        (for [tick [max-value (* 0.75 max-value) (* 0.50 max-value) (* 0.25 max-value)]
              :let [tick-y (- height (* tick scale) my)]]
          [:g 
           [:text {:font-size "4pt" :text-anchor "end" :x (- mx 3) :y tick-y}
            (str tick)]
           [:line {:x1 mx :y1 tick-y :x2 width :y2 tick-y
                   :style "stroke:rgb(200,200,200);stroke-width:0.5;" :stroke-dasharray "5,1"}]])
        
        (map-indexed (fn [i d]
                       (let [label (label-fn d)
                             value (value-fn d)
                             bar-height (* value scale)
                             x (+ mx (* i bar-width))
                             y (- height bar-height my)] ;; FIXME: scale min-max
                         [:g 
                          [:rect {:x x
                                  :y y
                                  :width (* bar-width 0.75)
                                  :height bar-height
                                  :fill (color-fn d)}]
                          [:text {:font-size "4pt" :x (+ x (/ bar-width 2)) :y (- y 1) :text-anchor "end"} (str value)]
                          [:text {:font-size "4pt" :x (+ x (/ bar-width 2)) :y (+ 4 (+ y bar-height)) :text-anchor "end"}
                           label]]))
                     data)
        
        
        
        ])]]])




(defmethod muodosta-pdf :yhteenveto [[_ otsikot-ja-arvot]]
  ;;[:yhteenveto [[otsikko1 arvo1] ... [otsikkoN arvoN]]] -> yhteenveto (kuten päällystysilmoituksen alla)
  [:fo:table
   [:fo:table-column {:column-width "25%"}]
   [:fo:table-column {:column-width "75%"}]
   [:fo:table-body
    (for [[otsikko arvo] otsikot-ja-arvot]
      [:fo:table-row
       [:fo:table-cell
        [:fo:block {:text-align "right" :font-weight "bold"}
         (let [otsikko (str/trim (str otsikko))]
           (if (.endsWith otsikko ":")
             otsikko
             (str otsikko ":")))]]
       [:fo:table-cell
        [:fo:block {:margin-left "5mm"} (str arvo)]]])]])

(defmethod muodosta-pdf :raportti [[_ raportin-tunnistetiedot & sisalto]]
  ;; Muodosta header raportin-tunnistetiedoista!
  (apply fo/dokumentti {}
         (map muodosta-pdf sisalto)))
