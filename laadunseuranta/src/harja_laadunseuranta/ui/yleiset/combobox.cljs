(ns harja-laadunseuranta.ui.yleiset.combobox
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]))

(defn combobox [valinnat valinta-atom {:keys [luokka] :as optiot}]
  [:select {:name "valinnat" :class (str "combobox " (when luokka
                                                       luokka))}
   (doall (for [{:keys [avain nimi] :as valinta} valinnat]
            ^{:key avain}
            [:option {:value avain} nimi]))])