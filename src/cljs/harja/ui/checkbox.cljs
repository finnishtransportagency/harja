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
  [tila-atom nimi]
  (let [checkbox-tila->luokka {:valittu          "harja-checkbox-valittu"
                               :ei-valittu       "harja-checkbox-ei-valittu"
                               :osittain-valittu "harja-checkbox-osittain-valitu"}
        vaihda-tila (fn []
                      (case @tila-atom
                        :valittu (reset! tila-atom :ei-valittu)
                        :ei-valittu (reset! tila-atom :valittu)
                        :osittain-valittu (reset! tila-atom :ei-valittu)))]
    (fn []
      [:div.harja-checkbox
       [:div.harja-checkbox-laatikko {:class    (checkbox-tila->luokka @tila-atom)
                                      :on-click vaihda-tila}
        [:div.harja-checkbox-laatikko-sisalto
         (when (= :valittu @tila-atom)
                  [:img.harja-checkbox-rasti {:src "images/rasti.svg"}])]]
       [:div.harja-checkbox-teksti nimi]])))