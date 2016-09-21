(ns harja-laadunseuranta.urakkavalitsin
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.painike :as painike]
            [harja-laadunseuranta.schemas :as schemas]))

(defn- urakkavalitsin [urakkatyypin-urakat tarkastustyyppi valitse-fn]
  (let [urakkatyyppi (case tarkastustyyppi
                       :paallystys "paallystys"
                       :tiemerkinta "tiemerkinta"

                       nil)
        urakat-joissa-urakkaroolissa (filter #(= true (:urakkaroolissa? %))
                                             urakkatyypin-urakat)
        urakat-joissa-ei-urakkaroolissa (filter #(= false (:urakkaroolissa? %))
                                                urakkatyypin-urakat)
        listaus (fn [urakkalista]
                  [:table.urakkataulu
                   [:tbody
                    (map-indexed
                      (fn [i urakat]
                        ^{:key i}
                        [:tr.urakkarivi
                         (for [u urakat]
                           ^{:key (:id u)}
                           [:td.inline.urakkapainike {:on-click #(valitse-fn u)}
                            (:nimi u)])])
                      (partition-all 3 urakkalista))]])]
    [:div.urakkavalitsin
     [:h3.urakkaotsikko "Valitse tarkastuksen urakka"]
     (if (empty? urakkatyypin-urakat)
       [:span "Ei urakoita saatavilla urakkatyypille " urakkatyyppi]
       [:div.urakkataulut
        (when-not (empty? urakat-joissa-urakkaroolissa)
          [:div.urakoiden-valiotsikko "Omat urakat"])
        [listaus urakat-joissa-urakkaroolissa]
        (when-not (empty? urakat-joissa-urakkaroolissa)
          [:div.urakoiden-valiotsikko "Muut urakat"])
        [listaus urakat-joissa-ei-urakkaroolissa]])
     [:div
      [:span.tyhja-nappi]
      [:span.tyhja-nappi]
      [:button.peruuta.nappi-toissijainen {:on-click #(valitse-fn nil)}
       "Peruuta"]]]))

