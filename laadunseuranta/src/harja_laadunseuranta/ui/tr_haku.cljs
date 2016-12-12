(ns harja-laadunseuranta.ui.tr-haku
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [harja-laadunseuranta.macros :refer [with-delay-loop]]
                   [cljs.core.async.macros :refer [go-loop]]
                   [reagent.ratom :refer [run!]]))

(defn tr-selailukomponentti [nakyvissa model]
  [:div.tr-selailu {:class (when @nakyvissa "tr-selailu-auki")}
   [:p "Tierekisteritiedot"]
   [:div.tr-tieto
    [:span "Talvihoitoluokka"]
    [:span (or (:talvihoitoluokka @model) "-")]]
   [:div.tr-tieto
    [:span "Soratiehoitoluokka"]
    [:span (or (:soratiehoitoluokka @model) "-")]]])
