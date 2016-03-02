(ns harja.virhekasittely
  (:require [reagent.core :refer [atom]]))

(defn rendaa-virhe [e]
  (let [auki (atom false)]
    (fn [e]
      [:div.crash-component {:on-click #(swap! auki not)}
       [:span "Hupsista, Harja räsähti. Olemme pahoillamme, kuulisimme mielellään miten sait vian esiin."]
       [:div.crash-details {:class (if @auki "details-open" "")}
        (if (instance? js/Error e)
          (.-stack e)
          e)]])))

(defn arsyttava-virhe [& msgs]
  (.alert js/window (apply str msgs)))
