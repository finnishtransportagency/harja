(ns harja.ui.kartta-debug
  (:require [harja.views.kartta.tasot :as tasot]
            [harja.ui.dom :as dom]
            [reagent.core :as r :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]
            [harja.ui.kentat :as kentat])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:nayta-kaikki-layerit? false}))
(defonce layers (reaction (into {} (map (fn [[kerros kerroksen-tila-atom]]
                                      [kerros @kerroksen-tila-atom])
                                    tasot/tasojen-nakyvyys-atomit))))

(defn kartta-layers
  []
  (fn []
    [:div#kartta-debug {:style {:position "absolute"}}
     [:div {:style {:display "flex"
                    :align-content "flex-start"
                    :height "inherit"
                    :overflow (if @nav/kartta-nakyvissa?
                                "visible"
                                "hidden")}}
      (doall
        (keep (fn [[taso paalla?]]
                (when (or paalla?
                          (:nayta-kaikki-layerit? @tila))
                  ^{:key taso}
                  [:div {:style {:flex 1}}
                   [kentat/tee-kentta
                    {:tyyppi :checkbox
                     :teksti (str (name taso) ": " paalla?)}
                    (r/wrap true
                            #(reset! (taso tasot/tasojen-nakyvyys-atomit) %))]]))
              @layers))]]))

(defn aseta-kartta-debug-sijainti
  [x y w h h-debug naulattu?]
  (when-let
    [karttasailio (dom/elementti-idlla "kartta-debug")]
    (let [tyyli (.-style karttasailio)]
      ;;(log "ASETA-KARTAN-SIJAINTI: " x ", " y ", " w ", " h ", " naulattu?)
      (if naulattu?
        (do
          (set! (.-position tyyli) "fixed")
          (set! (.-left tyyli) (fmt/pikseleina x))
          (set! (.-top tyyli) (fmt/pikseleina (+ y h)))
          (set! (.-width tyyli) (fmt/pikseleina w))
          (set! (.-height tyyli) (fmt/pikseleina h)))
        (do
          (set! (.-position tyyli) "absolute")
          (set! (.-left tyyli) (fmt/pikseleina x))
          (set! (.-top tyyli) (fmt/pikseleina (+ y h)))
          (set! (.-width tyyli) (fmt/pikseleina w))
          (set! (.-height tyyli) (fmt/pikseleina h))))
      (set! (.-position tyyli) (-> "kartta-container" dom/elementti-idlla .-style .-position str))
      (when (= :S @nav/kartan-koko)
        (set! (.-left tyyli) "")
        (set! (.-right tyyli) (fmt/pikseleina 20))
        (set! (.-width tyyli) (fmt/pikseleina 100))))))