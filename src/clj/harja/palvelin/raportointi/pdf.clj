(ns harja.palvelin.raportointi.pdf
  "Raportoinnin elementtien renderöinti PDF:ksi"
  (:require [harja.tyokalut.xsl-fo :as fo]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defmulti muodosta-pdf
  "Muodostaa PDF:n XSL-FO hiccupin annetulle raporttielementille.
  Dispatch tyypin mukaan (vektorin 1. elementti)."
  (fn [elementti]
    (assert (and (vector? elementti)
                 (> (count elementti) 1)
                 (keyword? (first elementti)))
            (str "Raporttielementin on oltava vektori, jonka 1. elementti on tyyppi ja muut sen sisältöä, sain: "
                 (pr-str elementti)))
    (first elementti)))

(defmethod muodosta-pdf :taulukko [[_ {:keys [otsikko viimeinen-rivi-yhteenveto?] :as optiot} sarakkeet data]]
  [:fo:block {} otsikko
   [:fo:table {:border "solid 0.1mm black"}
    (for [{:keys [otsikko leveys]} sarakkeet]
      [:fo:table-column {:column-width leveys}])
    [:fo:table-header
     [:fo:table-row
      (for [otsikko (map :otsikko sarakkeet)]
        [:fo:table-cell {:border "solid 0.1mm black" :background-color "#afafaf" :font-weight "bold" :padding "1mm"}
         [:fo:block otsikko]])]]
    [:fo:table-body
     (let [viimeinen-rivi (last data)]
       (for [rivi data]
         (let [korosta? (when (and viimeinen-rivi-yhteenveto?
                                   (= viimeinen-rivi rivi))
                          {:font-weight "bold"})]
           [:fo:table-row
            (for [i (range (count sarakkeet))
                  :let [arvo (nth rivi i)]]
              [:fo:table-cell (merge {:border "solid 0.1mm black" :padding "1mm"}
                                     korosta?)
               [:fo:block (str arvo)]])])))]]
   [:fo:block {:space-after "1em"}]])


(defmethod muodosta-pdf :otsikko [[_ teksti]]
  [:fo:block {:padding-top "5mm" :font-size "16pt"} teksti])

(defmethod muodosta-pdf :teksti [[_ teksti]]
  [:fo:block {} teksti])

(defmethod muodosta-pdf :pylvaat [[_ {:keys [otsikko vari]} pylvaat]]
  ;;[:pylvaat "Otsikko" [[pylvas1 korkeus1] ... [pylvasN korkeusN]]] -> bar chart svg
  [:fo:block
   [:fo:block {:font-weight "bold"} otsikko]
   [:fo:instream-foreign-object {:content-width "17cm" :content-height "10cm"}
    [:svg {:xmlns "http://www.w3.org/2000/svg"}
     (let [data pylvaat
           width 180
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
                     data)])]]])

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

(defn- luo-header [raportin-nimi]
  (let [nyt (.format (java.text.SimpleDateFormat. "dd.MM.yyyy HH:mm") (java.util.Date.))]
    [:fo:table
     [:fo:table-column {:column-width "40%"}]
     [:fo:table-column {:column-width "40%"}]
     [:fo:table-column {:column-width "20%"}]
     [:fo:table-body
      [:fo:table-row
       [:fo:table-cell [:fo:block raportin-nimi]]
       [:fo:table-cell [:fo:block "Ajettu " nyt]]
       [:fo:table-cell {:text-align "end"}
        [:fo:block
         "Sivu " [:fo:page-number] " / " [:fo:page-number-citation {:ref-id "raportti-loppu"}]]]]]]))
  
(defmethod muodosta-pdf :raportti [[_ raportin-tunnistetiedot & sisalto]]
  ;; Muodosta header raportin-tunnistetiedoista!
  (apply fo/dokumentti {:orientation (or (:orientaatio raportin-tunnistetiedot) :portrait)
                        :header {:sisalto (luo-header (:nimi raportin-tunnistetiedot))}}
         (concat [;; Jos raportin tunnistetiedoissa on annettu :tietoja avaimella, näytetään ne alussa
                  (when-let [tiedot (:tietoja raportin-tunnistetiedot)]
                    [:fo:block {:padding "2mm" :border "solid 0.2mm black" :margin-bottom "2mm"}
                     (muodosta-pdf [:yhteenveto tiedot])])]
                 (keep identity
                       (mapcat #(when %
                                 (if (seq? %)
                                   (map muodosta-pdf %)
                                   [(muodosta-pdf %)]))
                               sisalto))
                 [[:fo:block {:id "raportti-loppu"}]])))
