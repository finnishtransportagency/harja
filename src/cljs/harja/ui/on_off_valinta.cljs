(ns harja.ui.on-off-valinta
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn on-off-valinta
  "Ottaa tila-atomin, joka määrittelee komponentin tilan. Tila-atomin mahdolliset arvot true ja false."
  [tila opts]
  (let [kasittely-fn (:on-change opts)
        vaihda-tila (fn []
                      (if @tila
                        (reset! tila false)
                        (reset! tila true)))]
    [:div {:class    (str "harja-on-off-valinta " (when (:luokka opts) (:luokka opts)))
           :on-click (fn []
                       (vaihda-tila)
                       (when kasittely-fn
                         (kasittely-fn)))}
     [:div.harja-on-off-pohja
      [:div.harja-on-off-pallo {:style (if @tila
                                         {:left "0px"}
                                         {:left "30px"})}]]]))