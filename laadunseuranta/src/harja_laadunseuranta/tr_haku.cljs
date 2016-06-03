(ns harja-laadunseuranta.tr-haku
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.comms :as comms]
            [cljs.core.async :refer [<!]])
  (:require-macros [harja-laadunseuranta.macros :refer [with-delay-loop]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [run!]]))

(defn alusta-tr-haku [sijainti-atomi tr-tiedot]
  (run!
   (when-let [pos (:nykyinen @sijainti-atomi)]
     (go
       (let [result (:ok (<! (comms/hae-tr-tiedot pos)))]
         (reset! tr-tiedot result))))))

(defn tr-selailukomponentti [nakyvissa model]
  [:div.tr-selailu {:class (when @nakyvissa "tr-selailu-auki")}
   [:p "Tierekisteritiedot"]
   [:div.tr-tieto
    [:span "Talvihoitoluokka"]
    [:span (or (:talvihoitoluokka @model) "-")]]
   [:div.tr-tieto
    [:span "Soratiehoitoluokka"]
    [:span (or (:soratiehoitoluokka @model) "-")]]])
