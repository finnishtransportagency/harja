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

(defn kumousboksi [{:keys [nakyvissa? piilossa-x piilossa-y nakyvissa-x nakyvissa-y kumoa-fn sulje-fn]}]
  (let [tila (atom :tallennettu)
        nyky-sijainti (atom [piilossa-x piilossa-y])]
    (komp/luo
      (komp/kun-muuttuu (fn [{:keys [nakyvissa?]}]
                          (if nakyvissa?
                            (reset! nyky-sijainti [nakyvissa-x nakyvissa-y])
                            (do
                              (reset! nyky-sijainti [piilossa-x piilossa-y])
                              (reset! tila :tallennettu)))))
      (fn [{:keys [lahto-x lahto-y loppu-x loppu-y kumoa-fn]}]
        ;; FIXME Laskenta menee vikaan jos sivun leveys muuttuu
        [:div.kumousboksi {:style {:left (first @nyky-sijainti)
                                   :top (second @nyky-sijainti)}}
         [napit/sulje-ruksi sulje-fn]
         [:p (case @tila
               :tallennettu "Muutos tallennettu!"
               :kumotaan "Kumotaan...")]
         [napit/kumoa "Kumoa"
          (fn []
            (reset! tila :kumotaan)
            (kumoa-fn))
          {:disabled (= @tila :kumotaan)}]]))))