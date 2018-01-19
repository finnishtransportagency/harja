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

(defn kumousboksi [{:keys [lahto-x lahto-y loppu-x loppu-y kumoa-fn]}]
  (let [lahto-sijainti [lahto-x lahto-y]
        piirretty-aika (atom nil)
        tila (atom :tallennettu)
        nyky-sijainti (atom [lahto-x lahto-y])]
    (komp/luo
      (komp/piirretty #(do (reset! nyky-sijainti [loppu-x loppu-y])
                           (reset! piirretty-aika (t/now))))
      (fn [{:keys [lahto-x lahto-y loppu-x loppu-y kumoa-fn]}]
        [:div.kumousboksi {:style {:left (first @nyky-sijainti)
                                   :top (second @nyky-sijainti)}}
         [napit/sulje-ruksi (constantly nil)]
         [:p (case @tila
               :tallennettu "Muutos tallennettu!"
               :kumotaan "Kumotaan...")]
         [napit/kumoa "Kumoa"
          (fn []
            (reset! tila :kumotaan)
            (kumoa-fn))
          {:disabled (= @tila :kumotaan)}]]))))