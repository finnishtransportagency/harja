(ns harja-laadunseuranta.ui.kamera
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.kamera :as tiedot]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn file-input [on-change]
  [:div.file-input-container
   [:input {:id "file-input"
            :type "file"
            :accept "image/*"
            :capture true
            :on-change on-change}]])

(defn kamerakomponentti [esikatselukuva-atom]
  [:div.kameranappi {:on-click tiedot/ota-kuva}
   [:div.kameranappi-sisalto
    (if @esikatselukuva-atom
     [:img {:width "100px" :src @esikatselukuva-atom}]
     [:div.kamera-eikuvaa
      [kuvat/svg-sprite "kamera-24"]
      "Lisää kuva"])]])

(defonce testikuva (atom nil))

(defcard kamerakomponentti-card
  "Kamerakomponentti"
  (fn [kuva _]
    (reagent/as-element
      [:div
       [:img {:src (or @kuva "")
              :width "500px"
              :height "400px"}]
       [kamerakomponentti kuva]]))
  testikuva
  {:watch-atom true})
