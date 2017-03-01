(ns harja-laadunseuranta.ui.yleiset.varmistusdialog
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.yleiset.napit :refer [nappi]]))

(defn tyhjenna! []
  (reset! s/varmistusdialog-data nil))

(defn varmistusdialog-komponentti [varmistusdialog-data]
  [:div.varmistusdialog-container
   [:div.varmistusdialog
    [:div.varmistus (:teksti varmistusdialog-data)]
    [:div.vastaus
     [nappi (:positiivinen-vastaus-teksti varmistusdialog-data)
      {:luokat-str "nappi-ensisijainen"
       :on-click #(do (when-let [on-click (:positiivinen-fn varmistusdialog-data)]
                        (on-click))
                      (tyhjenna!))}]
     [nappi (:negatiivinen-vastaus-teksti varmistusdialog-data)
      {:luokat-str "nappi-toissijainen"
       :on-click #(do (when-let [on-click (:negatiivinen-fn varmistusdialog-data)]
                        (on-click))
                      (tyhjenna!))}]]]])

(defn varmista! [teksti {:keys [positiivinen-vastaus-teksti negatiivinen-vastaus-teksti
                                positiivinen-fn negatiivinen-fn] :as optiot}]
  (let [varmistusdialog-data {:teksti teksti
                              :positiivinen-vastaus-teksti positiivinen-vastaus-teksti
                              :negatiivinen-vastaus-teksti negatiivinen-vastaus-teksti
                              :positiivinen-fn positiivinen-fn
                              :negatiivinen-fn negatiivinen-fn}]
    (reset! s/varmistusdialog-data varmistusdialog-data)))