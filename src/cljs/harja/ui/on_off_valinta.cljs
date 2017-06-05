(ns harja.ui.on-off-valinta
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def ^{:private true
       :doc "Luokat boolean tilan perusteella"}
  tila-luokat {true "harja-on-off-tila-on"
               false "harja-on-off-tila-off"})

(defn on-off-valinta
  "Ottaa tila-atomin, joka määrittelee komponentin tilan. Tila-atomin mahdolliset arvot true ja false."
  [tila opts]
  (let [kasittely-fn (:on-change opts)
        vaihda-tila #(swap! tila not)]
    [:div {:class    (str "harja-on-off-valinta " (when (:luokka opts) (:luokka opts)))
           :on-click (fn []
                       (vaihda-tila)
                       (when kasittely-fn
                         (kasittely-fn)))}
     [:div.harja-on-off-pohja
      [:div.harja-on-off-pallo {:class (tila-luokat @tila)}]]]))

(defn on-off [off-teksti on-teksti nykyinen-tila toggle!]
  [:div.harja-on-off-otsikolla
   [:div.harja-on-off-otsikko off-teksti]
   [on-off-valinta (r/wrap nykyinen-tila toggle!) {:luokka "inline-block"}]
   [:div.harja-on-off-otsikko on-teksti]])
