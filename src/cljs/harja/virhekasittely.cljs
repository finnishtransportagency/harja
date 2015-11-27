(ns harja.virhekasittely
  (:require [reagent.core :refer [atom]]))

(defn rendaa-virhe [e]
  (let [auki (atom false)]
    (fn [e]
      [:div.crash-component {:on-click #(swap! auki not)}
       [:span "Ups... noloa. Pieniä teknisiä ongelmia, lataathan sivun uudelleen?"]
       [:div.crash-details {:class (if @auki "details-open" "")}
        (if (instance? js/Error e)
          (.-stack e)
          e)]])))
