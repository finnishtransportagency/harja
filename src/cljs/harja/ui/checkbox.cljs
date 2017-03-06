(ns harja.ui.checkbox
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]

            [cljs.core.async :refer [<!]])
  (:require-macros
    [reagent.ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]))

(def checkbox-tila-keyword->boolean
  {:valittu true
   :ei-valittu false})

(def boolean->checkbox-tila-keyword
  {true :valittu
   false :ei-valittu})

(def ^:const
checkbox-tila->luokka {:valittu "harja-checkbox-valittu"
                       :ei-valittu "harja-checkbox-ei-valittu"
                       :osittain-valittu "harja-checkbox-osittain-valittu"})

(defn checkbox
  "Ottaa checkbox-tila atomin, joka määrittelee komponentin tilan.
  Tila-atomin mahdolliset arvot:
  :valittu, :ei-valittu, :osittain-valittu
  Ottaa myös nimen, joka ilmestyy checkboxin viereen. Voi olla nil, jos tekstiä
  ei haluta (tai teksti ei ole osa tätä komponenttia)
  Lisäksi ottaa mapin erilaisia optioita"
  ([tila-atom] (checkbox tila-atom nil {}))
  ([tila-atom nimi] (checkbox tila-atom nimi {}))
  ([tila-atom nimi {:keys [on-change width otsikon-luokka] :as optiot}]
   (let [tila @tila-atom
         vaihda-tila (fn []
                       (let [uusi-tila (case tila
                                         :valittu :ei-valittu
                                         :ei-valittu :valittu
                                         :osittain-valittu :ei-valittu)]
                         (reset! tila-atom uusi-tila)
                         (when on-change
                           (on-change uusi-tila))))]
     [:div.harja-checkbox
      [:div.harja-checkbox-sisalto {:style {:width (or width "100%")}
                                    :on-click (fn [event]
                                                (vaihda-tila)
                                                (.stopPropagation event))}
       [:div.harja-checkbox-column
        [:div.harja-checkbox-laatikko {:class (checkbox-tila->luokka tila)}
         [:div.harja-checkbox-laatikko-sisalto
          (when (= :valittu @tila-atom)
            [:img.harja-checkbox-rasti {:src "images/rasti.svg"}])]]]
       [:div.harja-checkbox-column
        [:div {:class (str "harja-checkbox-teksti" (when otsikon-luokka (str " " otsikon-luokka)))}
         nimi]]]])))
