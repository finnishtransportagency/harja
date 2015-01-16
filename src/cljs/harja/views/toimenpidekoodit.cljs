(ns harja.views.toimenpidekoodit
  "Toimenpidekoodien ylläpitonäkymä"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.asiakas.kommunikaatio :as k]
            [clojure.string :as str]))

(defonce koodit (atom nil))

(defonce haku (atom ""))
(defonce valittu-toimenpidekoodi (atom nil))

(defn hae-koodeja [termi]
  (let [termi (.toLowerCase termi)]
    (vec (filter #(let [[koodi {nimi :nimi}] %]
                    (or (not= -1 (.indexOf koodi termi))
                        (not= -1 (.indexOf (.toLowerCase nimi) termi))))
                 (seq @koodit)))))


(def toimenpidekoodit
  "Toimenpidekoodien hallinnan pääkomponentti"
  (with-meta 
    (fn []
      [:span
       [:input {:on-change #(reset! haku (-> % .-target .-value))
                :placeholder "Hae toimenpidekoodeista..."
                :value @haku}]
       (when (> (count @haku) 2)
         [:ul
          (for [[koodi tpk] (hae-koodeja @haku)]
            [:li {:on-click #(reset! valittu-toimenpidekoodi
                                     (assoc tpk :koodi koodi))}
             [:b koodi] " " (:nimi tpk)])])])
    {:component-did-mount (fn [this]
                            (k/post! :hae-toimenpidekoodit nil
                                     #(reset! koodit %))
                            (.log js/console "hallitaanpas vähän toimenpidekoodeja!"))}))

  
  
