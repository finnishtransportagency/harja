(ns harja.ui.checkbox
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def checkbox-tila-keyword->boolean
  {:valittu    true
   :ei-valittu true})

(def checkbox-boolean-tila->keyword
  {true :valittu
   false :ei-valittu})

(defn checkbox
  "Ottaa checkbox-tila atomin, joka määrittelee komponentin tilan. Tila-atomin mahdolliset arvot:
  :valittu, :ei-valittu, :osittain-valittu
  Lisäksi ottaa nimen, joka ilmestyy checkboxin viereen (jos nimen halutaan olevan teksti tämän
  komponentin ulkopuolella, voidaan antaa nil."
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