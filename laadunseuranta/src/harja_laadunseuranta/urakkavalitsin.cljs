(ns harja-laadunseuranta.urakkavalitsin
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.painike :as painike]
            [harja-laadunseuranta.schemas :as schemas]))

(defn- urakkavalitsin [urakkatyypin-urakat tarkastustyyppi valitse-fn]
  (let [urakkatyyppi (case tarkastustyyppi
                       :paallystys "paallystys"
                       :tiemerkinta "tiemerkinta"

                       nil)]
    [:div.urakkavalitsin
     [:h3 "Valitse urakka"]
     (if (empty? urakkatyypin-urakat)
       [:span "Ei urakoita saatavilla urakkatyypille " urakkatyyppi]
       [:ul.urakkalista
        (map-indexed
          (fn [i urakka]
            ^{:key i}
            [:li.urakkalistan-item.klikattava
             {:on-click #(valitse-fn urakka)}
             [:div.urakkalistan-urakka
              (:nimi urakka)]])
          urakkatyypin-urakat)])
     [:div
      [:span.tyhja-nappi]
      [:span.tyhja-nappi]
      [:button.peruuta.nappi-toissijainen {:on-click #(println "painettu")} "Peruuta"]]]))

