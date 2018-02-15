(ns harja.ui.kartta-debug
  "Näyttää kartan tilan. Defaulttina näytetään layerit, jotka on aktiivisiksi merkattu.
   Layereiden checkboxit on disabloitu, mikäli niiden näyttämää dataa ei ole haettu."
  (:require [harja.views.kartta.tasot :as tasot]
            [harja.ui.dom :as dom]
            [reagent.core :as r :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]
            [harja.ui.kentat :as kentat])
  (:require-macros [reagent.ratom :refer [reaction]]))

(declare aseta-kartta-debug-sijainti)

(defonce tila (atom {:nayta-kartan-debug? true
                     :nayta-kaikki-layerit? false
                     :nayta-kartan-ylaosassa? true
                     :kartan-paikka []}))
(defonce layers (reaction (into {} (map (fn [[kerros kerroksen-tila-atom]]
                                          [kerros @kerroksen-tila-atom])
                                        tasot/tasojen-nakyvyys-atomit))))

(defonce geometriat (reaction (into {} (map (fn [[kerros kerroksen-geometria]]
                                              [kerros @kerroksen-geometria])
                                            tasot/geometrioiden-atomit))))

(defn- checkbox-kentta
  [{:keys [teksti checked? disabled? on-change]}]
  [:div.checkbox {:style {:pointer-events "auto"}}
   [:label {:on-click #(.stopPropagation %)}
    [:input {:type "checkbox"
             :checked checked?
             :disabled disabled?
             :on-change on-change
             :data-tooltip "foo"}]
    teksti]])

(defn- nayta-asetukset []
  (let [optiot-fn (fn [teksti avain & toimintoja]
                    {:teksti teksti :checked? (avain @tila) :disabled? false
                     :on-change #(let [valittu? (-> % .-target .-checked)]
                                   (swap! tila assoc avain valittu?)
                                   (when (not-empty toimintoja)
                                     (doseq [toiminto toimintoja]
                                       (toiminto))))})]
    [:div
     [checkbox-kentta (optiot-fn "Nayta kartan debug?" :nayta-kartan-debug?)]
     [checkbox-kentta (optiot-fn "Nayta kaikki layerit?" :nayta-kaikki-layerit?)]
     [checkbox-kentta (optiot-fn "Nayta kartan yläosassa?" :nayta-kartan-ylaosassa? #(apply aseta-kartta-debug-sijainti (:kartan-paikka @tila)))]]))

(defn- nayta-layersit []
  [:div {:style {:display "flex"
                 :flex-flow "column wrap"
                 :pointer-events "none"}}
   (doall
     (sort-by #(-> % last :jarjestys)
              (keep (fn [[taso paalla?]]
                      (when (or paalla?
                                (:nayta-kaikki-layerit? @tila))
                        (let [tason-geometria (taso @geometriat)
                              geometria? (some? tason-geometria)
                              on-change #(reset! (taso tasot/tasojen-nakyvyys-atomit) (-> % .-target .-checked))]
                          ^{:key taso}
                          [checkbox-kentta {:teksti [:span
                                                     [:span (str (name taso))]
                                                     [:button {:on-click #(do (.stopPropagation %)
                                                                              (cljs.pprint/pprint tason-geometria))}
                                                      (str " geometriat")]]
                                            :checked? paalla?
                                            :disabled? (not geometria?)
                                            :on-change on-change
                                            :jarjestys (name taso)}])))
                    @layers)))])

(defn kartta-layers
  []
  (when (:nayta-kartan-debug? @tila)
    [:div#kartta-debug {:style {:position "absolute"
                                :z-index "901"
                                :pointer-events "none"}}
     [:div {:style {:height "inherit"
                    :pointer-events "none"
                    :display "flex"
                    :overflow (if @nav/kartta-nakyvissa?
                                "visible"
                                "hidden")}}
      [nayta-asetukset]
      [nayta-layersit]]]))

(defn aseta-kartta-debug-sijainti
  [x y w h naulattu?]
  (swap! tila assoc :kartan-paikka [x y w h naulattu?])
  (when (:nayta-kartan-debug? @tila)
    (when-let
      [karttasailio (dom/elementti-idlla "kartta-debug")]
      (let [tyyli (.-style karttasailio)]
        ;;(log "ASETA-KARTAN-SIJAINTI: " x ", " y ", " w ", " h ", " naulattu?)
        (if naulattu?
          (do
            (set! (.-position tyyli) "fixed")
            (set! (.-left tyyli) (fmt/pikseleina (+ x 30)))
            (set! (.-top tyyli) (fmt/pikseleina (if (:nayta-kartan-ylaosassa? @tila)
                                                  y
                                                  (+ y h))))
            (set! (.-height tyyli) (fmt/pikseleina h)))
          (do
            (set! (.-position tyyli) "absolute")
            (set! (.-left tyyli) (fmt/pikseleina (+ x 30)))
            (set! (.-top tyyli) (fmt/pikseleina (if (:nayta-kartan-ylaosassa? @tila)
                                                  y
                                                  (+ y h))))
            (set! (.-height tyyli) (fmt/pikseleina h))))
        (set! (.-position tyyli) (-> "kartta-container" dom/elementti-idlla .-style .-position str))
        (when (= :S @nav/kartan-koko)
          (set! (.-left tyyli) "")
          (set! (.-right tyyli) (fmt/pikseleina 20)))))))

(defn nayta-kartan-debug []
  (swap! tila assoc :nayta-kartan-debug? true)
  (apply aseta-kartta-debug-sijainti (:kartan-paikka @tila)))