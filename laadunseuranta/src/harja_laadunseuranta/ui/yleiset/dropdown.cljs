(ns harja-laadunseuranta.ui.yleiset.dropdown
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]))

(defn dropdown [valinnat {:keys [valinta-avain valinta-teksti] :as optiot}]
  (let [valinta-avain (or valinta-avain :id)
        valinta-teksti (or valinta-teksti :nimi)]
    (fn [valinnat {:keys [luokka valittu] :as optiot}]
      [:div.dropdown-container
       [:select {:name "valinnat" :class (str "dropdown " (when luokka
                                                            luokka))
                 :on-change valittu}
        (doall (for [valinta valinnat]
                 ^{:key (valinta-avain valinta)}
                 [:option {:value (valinta-avain valinta)}
                  (valinta-teksti valinta)]))]
       [:div.dropdown-nuoli
        [kuvat/svg-sprite "suunta-alas-24"]]])))