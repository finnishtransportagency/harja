(ns harja.views.main
  "Harjan päänäkymä"
  (:require [bootstrap :as bs]
            [reagent.core :refer [atom]]
            [harja.ui.leaflet :refer [leaflet]]))


(def page (atom :kartta))

(defn set-page!
  "Vaihda nykyinen sivu haluttuun."
  [new-page]
  (.log js/console "new page is: "
        (reset! page new-page)))

(defn header []
  [bs/navbar {}
   "Harja"
   [:form.navbar-form.navbar-left {:role "search"}
    [:div.form-group
     [:input.form-control {:type "text" :placeholder "Hae..."}]]
    [:button.btn.btn-default {:type "button"} "Hae"]]

   [:a {:href "#" :on-click #(set-page! :kartta)} "Kartta"]
   [:a {:href "#" :on-click #(set-page! :urakat)} "Urakat"]
   [:a {:href "#" :on-click #(set-page! :raportit)} "Raportit"]])

(defn footer []
  [:footer {:role "contentinfo"}
   "Liikenneviraston HARJA, FIXME: muuta footer tietoa tänne"])

; TODO: poista leikkidata kunhan saadaan oikeaa tialle
(def urakat 
  (atom [{:id 1 :name "Espoon alueurakka"}
         {:id 2 :name "Kuhmon alueurakka"}
         {:id 3 :name "Oulun alueurakka"}
         {:id 4 :name "Suomussalmen alueurakka"}
         {:id 5 :name "Vetelin alueurakka"}
         {:id 6 :name "Siikalatvan alueurakka"}
         {:id 7 :name "Raahe-Ylivieska alueurakka"}
         {:id 8 :name "Iin alueurakka"}
         {:id 9 :name "Kuopion alueurakka"}]))

(defn filtered-listing
  "Luettelo, jossa on hakukenttä filtteröinnille.
  opts voi sisältää
  :term hakutermin atomi
  :selection valitun listaitemin atomi
  :format funktio jolla itemi muutetaan stringiksi, oletus str
  list sisältää luettelon josta hakea."
  [opts list]
  (let [term (or (:term opts) (atom ""))
        valittu (or (:selection opts) (atom nil))
        fmt (or (:format opts) str)]
    (fn []
      [:div.haku-container
       [:input.haku-input.form-control
        {:type "text"
         :value @term
         :on-change #(do
                       (reset! term (.-value (.-target %)))
                       (.log js/console (-> % .-target .-value)))}]
       [:div.haku-lista-container
        [:ul.haku-lista
         (let [selected @valittu term @term]
           (for [i (filter #(not= (.indexOf (.toLowerCase (% :name)) (.toLowerCase term)) -1) @list)]
             ^{:key (:id i)}
             [:li.haku-lista-item
              {:on-click #(do
                            (reset! valittu i)
                            (.log js/console (str " selected on " selected)))
               :class (when (= i selected) "selected")}  
              [:div.haku-lista-item-nimi 
               (fmt i)]]))]]])))

;; esimerkkigeometrioita näytettäväksi kartalla
(def geometries (atom [{:type :polygon
                        :coordinates [[65.1 25.2]
                                      [65.15 25.2]
                                      [65.125 25.3]]}

                       {:type :line
                        :coordinates [[65.3 25.0]
                                      [65.4 25.5]]}]))

(def view-position (atom [65.1 25.2]))
(def zoom-level (atom 8))

(defn demo-kartta []
  (let [drawing (atom false)]
    (fn []
    [:span
     [leaflet {:id "kartta"
               :width "100%" :height "300px" ;; set width/height as CSS units
               :view view-position ;; map center position
               :zoom zoom-level ;; map zoom level

               ;; The actual map data (tile layers from OpenStreetMap), also supported is
               ;; :wms type
               :layers [{:type :tile
                         :url "http://{s}.tile.osm.org/{z}/{x}/{y}.png"
                         :attribution "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"}]

               ;; Geometry shapes to draw to the map
               :geometries geometries

               ;; Add handler for map clicks
               :on-click #(when @drawing
                            ;; if drawing, add point to polyline
                            (swap! geometries
                                   (fn [geometries]
                                     (let [pos (dec (count geometries))]
                                       (assoc geometries pos
                                         {:type :line
                                          :coordinates (conj (:coordinates (nth geometries pos))
                                                             %)})))))}
                                     ]
     [:div.actions
      "Control the map position/zoom by swap!ing the atoms"
      [:br]
      [:button {:on-click #(swap! view-position update-in [1] - 0.2)} "left"]
      [:button {:on-click #(swap! view-position update-in [1] + 0.2)} "right"]
      [:button {:on-click #(swap! view-position update-in [0] + 0.2)} "up"]
      [:button {:on-click #(swap! view-position update-in [0] - 0.2)} "down"]
      [:button {:on-click #(swap! zoom-level inc)} "zoom in"]
      [:button {:on-click #(swap! zoom-level dec)} "zoom out"]]

     (if @drawing
       [:span
        [:button {:on-click #(do
                              (swap! geometries
                                     (fn [geometries]
                                       (let [pos (dec (count geometries))]
                                         (assoc geometries pos
                                           {:type :polygon
                                            :coordinates (:coordinates (nth geometries pos))}))))
                              (reset! drawing false))}
         "done drawing"]
        "start clicking points on the map, click \"done drawing\" when finished"]

       [:button {:on-click #(do
                              (.log js/console "drawing a poly")
                              (reset! drawing true)
                              (swap! geometries conj {:type :line
                                                      :coordinates []}))} "draw a polygon"])

     [:div.info
      [:b "current view pos: "] (pr-str @view-position) [:br]
      [:b "current zoom level: "] (pr-str @zoom-level)]

   ])))

(defn page-kartta
  "Harjan karttasivu."
  []
  [:span
   [:div#sidebar-left.col-sm-4
    [:h5.haku-otsikko "Hae alueurakka kartalta tai listasta"]
    [:div [filtered-listing {:format :name} urakat]]]
   [:div#kartta-container.col-sm-4
    [demo-kartta]]
   ])

(defn main
  "Harjan UI:n pääkomponentti"
  []
  [:span
   [header]
   (case @page
     :kartta [:div [page-kartta]]
     :urakat [:div "jotain urakoita täällä"]
     :raportit [:div "täältä kätevästi raportteihin"])
   [footer]
   ])

