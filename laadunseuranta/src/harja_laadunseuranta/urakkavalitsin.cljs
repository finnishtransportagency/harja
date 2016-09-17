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
       [:div
        (map-indexed
          (fn [i urakat]
            ^{:key i}
            [:div.painikerivi
             (for [u urakat]
               ^{:key (:id u)}
               [:button.pikavalintapainike {:on-click #(valitse-fn u)}
                (:nimi u)])])
          (partition-all 3 urakkatyypin-urakat))])
     [:div
      [:span.tyhja-nappi]
      [:span.tyhja-nappi]
      [:button.peruuta.nappi-toissijainen {:on-click #(valitse-fn nil)}
       "Peruuta"]]]))

