(ns harja.ui.loader
  (:require [reagent.core :as r]))

(defn lataus-viestit
  "Tähän voi lisätä halutessa kiertäviä viestejä"
  [viesti]
  [viesti
   "Odota hetki..."
   "Ladataan..."])


(defn loader [viesti]
  (r/with-let [naytettava-viesti (r/atom (rand-nth (lataus-viestit viesti)))
               timer-id (atom (js/setInterval
                                #(reset! naytettava-viesti
                                   (rand-nth (lataus-viestit viesti)))
                                1300))]
    [:div.ajax-loader {:style {:width "100%"
                               :display "flex"
                               :flex-direction "column"
                               :align-items "center"}}
     [:div {:class "text-secondary mb-3"
            :style {:width "100%"
                    :text-align "center"}}
      @naytettava-viesti]
     [:div {:class "progress progress-sm"
            :style {:width "100%"}}
      [:div {:class "progress-bar progress-bar-indeterminate"}]]]
    (finally
      (js/clearInterval @timer-id))))
