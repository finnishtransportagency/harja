(ns harja.views.hallinta.harja-data.analyysi
  (:require [reagent.core :refer [atom wrap] :as r]
            [tuck.core :as tuck]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as y]
            [harja.ui.debug :as debug]
            [harja.tiedot.hallinta.harja-data.diagrammit :as tiedot]))

(defn yhteyskatkosanalyysi
  [{:keys [eniten-katkoksia pisimmat-katkokset rikkinaiset-lokitukset eniten-katkosryhmia]}]
  [:div
    [:p (str "Rikkinaisia lokituksia: " rikkinaiset-lokitukset)]
    [:p (str "Eniten katkoksia näillä palvelukutsuilla: " (pr-str eniten-katkoksia))]
    [:p (str "Eniten katkosryhmiä näillä palvelukutsuilla: " (pr-str eniten-katkosryhmia))]
    [:p (str "Pisimmät katkosvälit näillä palvelukutsuilla: " (pr-str pisimmat-katkokset))]])

(defn analyysi-paanakyma
  [e! {:keys [analyysi-tehty? analyysi]}]
  [:div
    [:h3 "Yhteyskatkosanalyysi"]
    (if analyysi-tehty?
      [yhteyskatkosanalyysi analyysi]
      [y/ajax-loader])])

(defn analyysi* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(when (empty? (:analyysi app))
                           (e! (tiedot/->HaeAnalyysi)))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div
        [debug/debug app]
        [analyysi-paanakyma e! app]])))

(defn analyysi []
  [tuck/tuck tiedot/app analyysi*])
