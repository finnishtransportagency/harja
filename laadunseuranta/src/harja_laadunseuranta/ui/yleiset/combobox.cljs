(ns harja-laadunseuranta.ui.yleiset.combobox
  (:require [reagent.core :as reagent :refer [atom]]))

(defn combobox [valinnat valinta-atom]
  [:select {:name "valinnat" :class "combobox"}
   (doall (for [{:keys [avain nimi] :as valinta} valinnat]
            ^{:key avain}
            [:option {:value avain} nimi]))])