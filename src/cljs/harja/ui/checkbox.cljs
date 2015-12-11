(ns harja.ui.checkbox
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]

            [cljs.core.async :refer [<!]])
  (:require-macros
    [reagent.ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]))

(def checkbox-tila-keyword->boolean
  {:valittu    true
   :ei-valittu false})

(def boolean->checkbox-tila-keyword
  {true :valittu
   false :ei-valittu})

(defn checkbox
  "Ottaa checkbox-tila atomin, joka määrittelee komponentin tilan. Tila-atomin mahdolliset arvot:
  :valittu, :ei-valittu, :osittain-valittu
  Ottaa myös nimen, joka ilmestyy checkboxin viereen. Voi olla nil, jos tekstiä ei haluta (tai teksti
  ei ole osa tätä komponenttia)
  Lisäksi ottaa mapin erilaisia optioita"
  [tila-atom nimi {:keys [on-change] :as optiot}]
  (let [on-change-fn on-change
        checkbox-tila->luokka {:valittu          "harja-checkbox-valittu"
                               :ei-valittu       "harja-checkbox-ei-valittu"
                               :osittain-valittu "harja-checkbox-osittain-valittu"}
        vaihda-tila (fn []
                      (let [uusi-tila (case @tila-atom
                                        :valittu :ei-valittu
                                        :ei-valittu :valittu
                                        :osittain-valittu :ei-valittu)]
                        (reset! tila-atom uusi-tila)
                        (when on-change-fn
                          (on-change-fn uusi-tila))))]
    (fn []
      [:div.harja-checkbox {:on-click vaihda-tila}
       [:div.harja-checkbox-laatikko {:class (checkbox-tila->luokka @tila-atom)}
        [:div.harja-checkbox-laatikko-sisalto
         (when (= :valittu @tila-atom)
           [:img.harja-checkbox-rasti {:src "images/rasti.svg"}])]]
       [:div.harja-checkbox-teksti nimi]])))