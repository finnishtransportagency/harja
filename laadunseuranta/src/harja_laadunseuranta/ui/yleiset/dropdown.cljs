(ns harja-laadunseuranta.ui.yleiset.dropdown
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]))

(defn dropdown [valinnat valinta-atom {:keys [luokka] :as optiot}]
  [:div.dropdown-container
   [:select {:name "valinnat" :class (str "dropdown " (when luokka
                                                       luokka))}
   (doall (for [{:keys [avain nimi] :as valinta} valinnat]
            ^{:key avain}
            [:option {:value avain} nimi]))]
   [:div.dropdown-nuoli
    [:img {:src kuvat/+avausnuoli+}]]])