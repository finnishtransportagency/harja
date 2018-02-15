(ns harja.ui.kartta-debug
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

(defn- nayta-asetukset []
  [:div
   [kentat/tee-kentta
    {:tyyppi :checkbox
     :teksti "Nayta kartan debug?"}
    (r/wrap (:nayta-kartan-debug? @tila)
            #(swap! tila assoc :nayta-kartan-debug? %))]
   [kentat/tee-kentta
    {:tyyppi :checkbox
     :teksti "Nayta kaikki layerit?"}
    (r/wrap (:nayta-kaikki-layerit? @tila)
            #(swap! tila assoc :nayta-kaikki-layerit? %))]
   [kentat/tee-kentta
    {:tyyppi :checkbox
     :teksti "Nayta kartan yläosassa?"}
    (r/wrap (:nayta-kartan-ylaosassa? @tila)
            #(do
               (swap! tila assoc :nayta-kartan-ylaosassa? %)
               (apply aseta-kartta-debug-sijainti (:kartan-paikka @tila))))]])

(defn- nayta-layersit []
  [:div
   (doall
     (keep (fn [[taso paalla?]]
             (when (or paalla?
                       (:nayta-kaikki-layerit? @tila))
               ^{:key taso}
               [:div {:style {:flex 1}}
                [kentat/tee-kentta
                 {:tyyppi :checkbox
                  :teksti (str (name taso) ": " paalla?)}
                 (r/wrap paalla?
                         #(reset! (taso tasot/tasojen-nakyvyys-atomit) %))]]))
           @layers))])

(defn kartta-layers
  []
  (when (:nayta-kartan-debug? @tila)
    [:div#kartta-debug {:style {:position "absolute"
                                :z-index "901"}}
     [:div {:style {:display "flex"
                    :align-content "flex-start"
                    :height "inherit"
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
            (set! (.-left tyyli) (fmt/pikseleina x))
            (set! (.-top tyyli) (fmt/pikseleina (if (:nayta-kartan-ylaosassa? @tila)
                                                  y
                                                  (+ y h))))
            (set! (.-width tyyli) (fmt/pikseleina w))
            (set! (.-height tyyli) (fmt/pikseleina h)))
          (do
            (set! (.-position tyyli) "absolute")
            (set! (.-left tyyli) (fmt/pikseleina x))
            (set! (.-top tyyli) (fmt/pikseleina (if (:nayta-kartan-ylaosassa? @tila)
                                                  y
                                                  (+ y h))))
            (set! (.-width tyyli) (fmt/pikseleina w))
            (set! (.-height tyyli) (fmt/pikseleina h))))
        (set! (.-position tyyli) (-> "kartta-container" dom/elementti-idlla .-style .-position str))
        (when (= :S @nav/kartan-koko)
          (set! (.-left tyyli) "")
          (set! (.-right tyyli) (fmt/pikseleina 20))
          (set! (.-width tyyli) (fmt/pikseleina 100)))))))

(defn nayta-kartan-debug []
  (swap! tila assoc :nayta-kartan-debug? true)
  (apply aseta-kartta-debug-sijainti (:kartan-paikka @tila)))