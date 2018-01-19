(ns harja.ui.kumousboksi
  (:require [reagent.core :as r]
            [reagent.core :refer [atom] :as r]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.fmt :as fmt]
            [goog.events.EventType :as EventType]
            [harja.loki :refer [log]]
            [harja.ui.ikonit :as ikonit]
            [cljs-time.core :as t])
  (:require-macros [harja.tyokalut.ui :refer [for*]]
                   [cljs.core.async.macros :refer [go]]))

(defn kumousboksi [{:keys [nakyvissa? piilossa-sijainti nakyvissa-sijainti kumoa-fn sulje-fn]}]
  (let [tila (atom :tallennettu)
        nyky-sijainti (atom piilossa-sijainti)]
    (komp/luo
      (komp/kun-muuttuu (fn [{:keys [nakyvissa?]}]
                          (if nakyvissa?
                            (reset! nyky-sijainti nakyvissa-sijainti)
                            (do
                              (reset! nyky-sijainti piilossa-sijainti)
                              (reset! tila :tallennettu)))))
      (fn [{:keys [nakyvissa? lahto-x lahto-y loppu-x loppu-y kumoa-fn]}]
        [:div.kumousboksi {:style {:left (:left @nyky-sijainti)
                                   :top (:top @nyky-sijainti)
                                   :bottom (:bottom @nyky-sijainti)
                                   :right (:right @nyky-sijainti)}}
         [napit/sulje-ruksi sulje-fn]
         [:p (case @tila
               :tallennettu "Muutos tallennettu!"
               :kumotaan "Kumotaan...")]
         [napit/kumoa "Kumoa"
          (fn []
            (reset! tila :kumotaan)
            (kumoa-fn))
          {:disabled (or (= @tila :kumotaan)
                         (not nakyvissa?))}]]))))