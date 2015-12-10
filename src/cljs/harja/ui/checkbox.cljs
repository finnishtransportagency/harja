(ns harja.ui.checkbox
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn checkbox
  "Ottaa checkbox-tila atomin, joka määrittelee komponentin tilan. Tila-atomin mahdolliset arvot:
  :valittu, :ei-valittu, :osittain-valittu
  Lisäksi ottaa nimen, joka ilmestyy checkboxin viereen (jos nimen halutaan olevan teksti tämän
  komponentin ulkopuolella, voidaan antaa nil.
  Lopuksi ottaa mapin muita optioita, joista tuettuna:
  - display, määrittelee komponentin CSS-display arvon (oletuksena inline-block)"
  [tila-atom nimi opts]
  (let [checkbox-tila->luokka {:valittu          "harja-checkbox-valittu"
                               :ei-valittu       "harja-checkbox-ei-valittu"
                               :osittain-valittu "harja-checkbox-osittain-valitu"}
        kasittele-click-eventti (fn []
                                  (case @tila-atom
                                    :valittu (reset! tila-atom :ei-valittu)
                                    :ei-valittu (reset! tila-atom :valittu)
                                    :osittain-valittu (reset! tila-atom :ei-valittu)))]
    (fn []
      [:div.harja-checkbox {:style {:display (or (:display opts) "inline-block")}}
       [:div.harja-checkbox-laatikko {:class    (checkbox-tila->luokka @tila-atom)
                                      :on-click kasittele-click-eventti}
        [:div.harja-checkbox-laatikko-sisalto]]
       [:div.harja-checkbox-teksti nimi]])))